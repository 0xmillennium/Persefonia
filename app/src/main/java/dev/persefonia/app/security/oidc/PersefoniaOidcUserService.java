package dev.persefonia.app.security.oidc;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

import dev.persefonia.app.identityaccess.bootstrap.AdminBootstrapService;
import dev.persefonia.app.security.admin.AdminPrincipal;
import dev.persefonia.identityaccess.domain.admin.AdminAccount;
import dev.persefonia.identityaccess.domain.admin.access.AdminAccessDeniedException;
import dev.persefonia.identityaccess.domain.admin.access.AdminIdentityClaims;

@Component
@Lazy
public final class PersefoniaOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {
    private final OidcClaimMapper claimMapper;
    private final AdminBootstrapService adminBootstrapService;
    private final OAuth2UserService<OidcUserRequest, OidcUser> delegate;

    @Autowired
    public PersefoniaOidcUserService(OidcClaimMapper claimMapper, AdminBootstrapService adminBootstrapService) {
        this(claimMapper, adminBootstrapService, new OidcUserService());
    }

    PersefoniaOidcUserService(
            OidcClaimMapper claimMapper,
            AdminBootstrapService adminBootstrapService,
            OAuth2UserService<OidcUserRequest, OidcUser> delegate) {
        this.claimMapper = Objects.requireNonNull(claimMapper, "claimMapper");
        this.adminBootstrapService = Objects.requireNonNull(adminBootstrapService, "adminBootstrapService");
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        try {
            OidcUser delegateUser = delegate.loadUser(userRequest);
            AdminIdentityClaims claims = claimMapper.toAdminIdentityClaims(delegateUser);
            AdminAccount account = adminBootstrapService.resolveOrBootstrap(claims).account();
            return new PersefoniaOidcUser(delegateUser, principalFrom(account));
        } catch (OAuth2AuthenticationException exception) {
            throw exception;
        } catch (AdminAccessDeniedException exception) {
            throw authenticationFailure("persefonia_admin_access_denied", "Admin access denied", exception);
        } catch (RuntimeException exception) {
            throw authenticationFailure("persefonia_oidc_authentication_failed", "OIDC authentication failed", exception);
        }
    }

    private static AdminPrincipal principalFrom(AdminAccount account) {
        return new AdminPrincipal(
                account.id(),
                account.oidcSubject(),
                account.email(),
                account.normalizedEmail(),
                account.displayName(),
                account.roles(),
                account.status());
    }

    private static OAuth2AuthenticationException authenticationFailure(
            String code,
            String description,
            RuntimeException cause) {
        return new OAuth2AuthenticationException(new OAuth2Error(code, description, null), cause);
    }
}
