package dev.persefonia.app.security.oidc;

import java.io.IOException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import dev.persefonia.app.identityaccess.session.TransactionalAdminSessionRevalidationGateway;
import dev.persefonia.app.security.admin.AdminPrincipal;
import dev.persefonia.identityaccess.domain.admin.AdminAccount;
import dev.persefonia.identityaccess.domain.admin.access.AdminAccessDeniedException;
import dev.persefonia.identityaccess.domain.admin.access.AdminIdentityClaims;

public class AdminOidcSessionRevalidationService {
    private final OAuth2AuthorizedClientManager authorizedClients;
    private final AdminOidcFreshUserService freshUsers;
    private final OidcClaimMapper claimMapper;
    private final TransactionalAdminSessionRevalidationGateway gateway;

    public AdminOidcSessionRevalidationService(OAuth2AuthorizedClientManager authorizedClients,
            AdminOidcFreshUserService freshUsers, OidcClaimMapper claimMapper,
            TransactionalAdminSessionRevalidationGateway gateway) {
        this.authorizedClients = Objects.requireNonNull(authorizedClients, "authorizedClients");
        this.freshUsers = Objects.requireNonNull(freshUsers, "freshUsers");
        this.claimMapper = Objects.requireNonNull(claimMapper, "claimMapper");
        this.gateway = Objects.requireNonNull(gateway, "gateway");
    }

    public PersefoniaOidcUser revalidate(OAuth2AuthenticationToken authentication, PersefoniaOidcUser currentUser,
            HttpServletRequest request, HttpServletResponse response) {
        Objects.requireNonNull(authentication, "authentication");
        Objects.requireNonNull(currentUser, "currentUser");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(response, "response");
        OAuth2AuthorizedClient client;
        try {
            client = authorizedClients.authorize(OAuth2AuthorizeRequest
                    .withClientRegistrationId(authentication.getAuthorizedClientRegistrationId())
                    .principal(authentication)
                    .attributes(attributes -> {
                        attributes.put(HttpServletRequest.class.getName(), request);
                        attributes.put(HttpServletResponse.class.getName(), response);
                    }).build());
        } catch (RuntimeException exception) {
            throw providerFailure(exception);
        }
        if (client == null || client.getRefreshToken() == null) {
            throw new AdminOidcSessionRevalidationException(AdminOidcSessionFailureReason.AUTHENTICATION_EXPIRED);
        }
        OidcUser freshUser;
        try {
            freshUser = freshUsers.loadFreshUser(client.getClientRegistration(), client.getAccessToken(),
                    currentUser.getIdToken());
        } catch (AdminOidcSessionRevalidationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw providerFailure(exception);
        }
        AdminIdentityClaims claims;
        try {
            claims = claimMapper.toAdminIdentityClaims(freshUser);
        } catch (OAuth2AuthenticationException exception) {
            throw new AdminOidcSessionRevalidationException(AdminOidcSessionFailureReason.INVALID_PROVIDER_RESPONSE);
        }
        if (!claims.oidcSubject().equals(currentUser.adminPrincipal().oidcSubject())) {
            throw new AdminOidcSessionRevalidationException(AdminOidcSessionFailureReason.INVALID_PROVIDER_RESPONSE);
        }
        try {
            AdminAccount account = gateway.revalidate(claims);
            return new PersefoniaOidcUser(freshUser, AdminPrincipal.from(account));
        } catch (AdminAccessDeniedException exception) {
            throw new AdminOidcSessionRevalidationException(AdminOidcSessionFailureReason.ACCESS_REVOKED);
        }
    }

    static AdminOidcSessionRevalidationException providerFailure(RuntimeException exception) {
        // Spring wraps transport/status failures; inspect typed causes, never provider error text.
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        boolean authorizationRejected = false;
        for (Throwable cause = exception; cause != null && visited.add(cause); cause = cause.getCause()) {
            if (cause instanceof ResourceAccessException || cause instanceof IOException) {
                return new AdminOidcSessionRevalidationException(AdminOidcSessionFailureReason.IDENTITY_PROVIDER_UNAVAILABLE);
            }
            if (cause instanceof RestClientResponseException response) {
                if (response.getStatusCode().is5xxServerError()) {
                    return new AdminOidcSessionRevalidationException(AdminOidcSessionFailureReason.IDENTITY_PROVIDER_UNAVAILABLE);
                }
                if (response.getStatusCode().value() == 401 || response.getStatusCode().value() == 403) {
                    authorizationRejected = true;
                }
            }
            String code = null;
            if (cause instanceof OAuth2AuthorizationException oauth2) {
                code = oauth2.getError().getErrorCode();
            } else if (cause instanceof OAuth2AuthenticationException oauth2) {
                code = oauth2.getError().getErrorCode();
            }
            if ("server_error".equals(code) || "temporarily_unavailable".equals(code)) {
                return new AdminOidcSessionRevalidationException(AdminOidcSessionFailureReason.IDENTITY_PROVIDER_UNAVAILABLE);
            }
            if (Set.of("invalid_grant", "invalid_token", "access_denied", "invalid_client", "unauthorized_client",
                    "insufficient_scope").contains(code == null ? "" : code)) {
                authorizationRejected = true;
            }
        }
        return new AdminOidcSessionRevalidationException(authorizationRejected
                ? AdminOidcSessionFailureReason.AUTHENTICATION_EXPIRED
                : AdminOidcSessionFailureReason.INVALID_PROVIDER_RESPONSE);
    }
}
