package dev.persefonia.app.security.oidc;

import java.net.http.HttpClient;
import java.util.Objects;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.client.RestTemplate;

public class AdminOidcFreshUserService {
    private final OAuth2UserService<OidcUserRequest, OidcUser> delegate;

    public AdminOidcFreshUserService(AdminOidcSessionProperties properties) {
        this(configuredUserService(properties));
    }

    AdminOidcFreshUserService(OAuth2UserService<OidcUserRequest, OidcUser> delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    public OidcUser loadFreshUser(ClientRegistration registration, OAuth2AccessToken accessToken,
            OidcIdToken existingIdToken) {
        Objects.requireNonNull(registration, "registration");
        Objects.requireNonNull(accessToken, "accessToken");
        Objects.requireNonNull(existingIdToken, "existingIdToken");
        try {
            OidcUser user = delegate.loadUser(new OidcUserRequest(registration, accessToken, existingIdToken));
            // A stale ID-token group must never fill in an omitted fresh groups claim.
            if (user == null || user.getUserInfo() == null || !user.getUserInfo().getClaims().containsKey("groups")) {
                throw new AdminOidcSessionRevalidationException(AdminOidcSessionFailureReason.INVALID_PROVIDER_RESPONSE);
            }
            return user;
        } catch (AdminOidcSessionRevalidationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw AdminOidcSessionRevalidationService.providerFailure(exception);
        }
    }

    private static OidcUserService configuredUserService(AdminOidcSessionProperties properties) {
        Objects.requireNonNull(properties, "properties").afterPropertiesSet();
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(properties.getOidcConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(client);
        requests.setReadTimeout(properties.getOidcReadTimeout());
        RestTemplate restTemplate = new RestTemplate(requests);
        restTemplate.setErrorHandler(new OAuth2ErrorResponseErrorHandler());
        DefaultOAuth2UserService oauth2UserService = new DefaultOAuth2UserService();
        oauth2UserService.setRestOperations(restTemplate);
        OidcUserService service = new OidcUserService();
        service.setOauth2UserService(oauth2UserService);
        service.setRetrieveUserInfo(request -> true);
        return service;
    }
}
