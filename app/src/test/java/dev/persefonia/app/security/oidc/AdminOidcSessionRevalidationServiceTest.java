package dev.persefonia.app.security.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.atLeastOnce;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.http.HttpStatus;

import dev.persefonia.app.identityaccess.session.TransactionalAdminSessionRevalidationGateway;
import dev.persefonia.app.security.admin.AdminPrincipal;
import dev.persefonia.identityaccess.application.admin.session.AdminSessionRevalidationUseCase;
import dev.persefonia.identityaccess.domain.admin.AdminAccount;
import dev.persefonia.identityaccess.domain.admin.AdminAccountId;
import dev.persefonia.identityaccess.domain.admin.AdminAccountRepository;
import dev.persefonia.identityaccess.domain.admin.AdminRole;
import dev.persefonia.identityaccess.domain.admin.DisplayName;
import dev.persefonia.identityaccess.domain.admin.EmailAddress;
import dev.persefonia.identityaccess.domain.admin.OidcGroup;
import dev.persefonia.identityaccess.domain.admin.OidcSubject;
import dev.persefonia.identityaccess.domain.admin.access.AdminAccessPolicy;

class AdminOidcSessionRevalidationServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");
    private final OAuth2AuthorizedClientManager manager = mock(OAuth2AuthorizedClientManager.class);
    private final AdminOidcFreshUserService freshUsers = mock(AdminOidcFreshUserService.class);
    private final AdminAccountRepository repository = mock(AdminAccountRepository.class);
    private final AdminOidcSessionRevalidationService service = new AdminOidcSessionRevalidationService(manager,
            freshUsers, new OidcClaimMapper(), new TransactionalAdminSessionRevalidationGateway(
            new AdminSessionRevalidationUseCase(repository, AdminAccessPolicy.of(OidcGroup.of("admin"), true, true))));
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    void currentLocalRolesWinInBothDirectionsAndRequestCarriesServletState() {
        for (AdminRole currentRole : AdminRole.values()) {
            for (AdminRole databaseRole : AdminRole.values()) {
                arrangeClient(true);
                when(freshUsers.loadFreshUser(any(), any(), any())).thenReturn(fresh("opaque-subject", List.of("admin", "owner")));
                AdminAccount currentAccount = account(databaseRole).recordSuccessfulLogin(NOW);
                when(repository.findByOidcSubject(any())).thenReturn(Optional.of(currentAccount));
                PersefoniaOidcUser result = revalidate(currentRole);
                assertThat(result.getAuthorities()).extracting(authority -> authority.getAuthority())
                        .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_" + databaseRole.name());
                assertThat(result.adminPrincipal()).isEqualTo(AdminPrincipal.from(currentAccount));
            }
        }
        verify(repository, never()).save(any());
        var capture = org.mockito.ArgumentCaptor.forClass(OAuth2AuthorizeRequest.class);
        verify(manager, atLeastOnce()).authorize(capture.capture());
        assertThat(capture.getValue().getClientRegistrationId()).isEqualTo("authelia");
        assertThat(capture.getValue().getAttributes()).containsEntry(jakarta.servlet.http.HttpServletRequest.class.getName(), request)
                .containsEntry(jakarta.servlet.http.HttpServletResponse.class.getName(), response);
    }

    @Test
    void groupRemovalDisabledOrDeletedLocalAccountRevokesAccess() {
        arrangeClient(true);
        when(freshUsers.loadFreshUser(any(), any(), any())).thenReturn(fresh("opaque-subject", List.of("user")));
        assertFailure(AdminOidcSessionFailureReason.ACCESS_REVOKED);
        verifyNoInteractions(repository);
        when(freshUsers.loadFreshUser(any(), any(), any())).thenReturn(fresh("opaque-subject", List.of("admin")));
        when(repository.findByOidcSubject(any())).thenReturn(Optional.of(account(AdminRole.OWNER).disable(NOW)));
        assertFailure(AdminOidcSessionFailureReason.ACCESS_REVOKED);
        when(repository.findByOidcSubject(any())).thenReturn(Optional.empty());
        assertFailure(AdminOidcSessionFailureReason.ACCESS_REVOKED);
        verify(repository, never()).save(any());
    }

    @Test
    void identityMismatchOrMalformedClaimsAreInvalidProviderResponse() {
        arrangeClient(true);
        when(freshUsers.loadFreshUser(any(), any(), any())).thenReturn(fresh("different-subject", List.of("admin")));
        assertFailure(AdminOidcSessionFailureReason.INVALID_PROVIDER_RESPONSE);
        when(freshUsers.loadFreshUser(any(), any(), any())).thenReturn(fresh("opaque-subject", "admin"));
        assertFailure(AdminOidcSessionFailureReason.INVALID_PROVIDER_RESPONSE);
        verifyNoInteractions(repository);
    }

    @Test
    void missingClientOrRefreshTokenExpiresAuthentication() {
        assertFailure(AdminOidcSessionFailureReason.AUTHENTICATION_EXPIRED);
        arrangeClient(false);
        assertFailure(AdminOidcSessionFailureReason.AUTHENTICATION_EXPIRED);
        verifyNoInteractions(freshUsers, repository);
    }

    @Test
    void invalidGrantExpiresAuthenticationAndProviderTransportOrServerFailureRetainsSessionEligibility() {
        when(manager.authorize(any())).thenThrow(new OAuth2AuthorizationException(new OAuth2Error("invalid_grant")));
        assertFailure(AdminOidcSessionFailureReason.AUTHENTICATION_EXPIRED);
        for (RuntimeException failure : List.of(new ResourceAccessException("sensitive-token"),
                new HttpServerErrorException(HttpStatus.BAD_GATEWAY),
                new OAuth2AuthorizationException(new OAuth2Error("temporarily_unavailable")))) {
            doThrow(failure).when(manager).authorize(any());
            assertFailure(AdminOidcSessionFailureReason.IDENTITY_PROVIDER_UNAVAILABLE);
        }
    }

    @Test
    void freshUserProtocolErrorsAreTranslatedSafely() {
        arrangeClient(true);
        when(freshUsers.loadFreshUser(any(), any(), any())).thenThrow(
                new OAuth2AuthenticationException(new OAuth2Error("invalid_token", "sensitive-token", null)));
        assertFailure(AdminOidcSessionFailureReason.AUTHENTICATION_EXPIRED);
        doThrow(new ResourceAccessException("sensitive-token")).when(freshUsers).loadFreshUser(any(), any(), any());
        assertFailure(AdminOidcSessionFailureReason.IDENTITY_PROVIDER_UNAVAILABLE);
    }

    private void assertFailure(AdminOidcSessionFailureReason reason) {
        assertThatThrownBy(() -> revalidate(AdminRole.OWNER))
                .isInstanceOfSatisfying(AdminOidcSessionRevalidationException.class,
                        exception -> assertThat(exception.reason()).isEqualTo(reason))
                .hasMessageNotContaining("opaque-subject").hasMessageNotContaining("admin@example.com")
                .hasMessageNotContaining("sensitive-token").hasMessageNotContaining("fake-id-token-value");
    }

    private PersefoniaOidcUser revalidate(AdminRole role) {
        PersefoniaOidcUser current = new PersefoniaOidcUser(OidcTestFixtures.validUser(), AdminPrincipal.from(account(role)));
        return service.revalidate(new OAuth2AuthenticationToken(current, current.getAuthorities(), "authelia"),
                current, request, response);
    }

    private void arrangeClient(boolean refresh) {
        var token = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "sensitive-token", NOW, NOW.plusSeconds(3600));
        when(manager.authorize(any())).thenReturn(new OAuth2AuthorizedClient(registration(), "local-principal", token,
                refresh ? new OAuth2RefreshToken("sensitive-refresh-token", NOW) : null));
    }

    static ClientRegistration registration() {
        return ClientRegistration.withRegistrationId("authelia").clientId("test-client").clientSecret("fake-client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email", "groups", "offline_access")
                .authorizationUri("https://auth.example/authorize").tokenUri("https://auth.example/token")
                .userInfoUri("https://auth.example/userinfo").userNameAttributeName("sub").build();
    }

    static AdminAccount account(AdminRole role) {
        return AdminAccount.create(AdminAccountId.newId(), OidcSubject.of("opaque-subject"),
                EmailAddress.of("admin@example.com"), DisplayName.of("Admin"), Set.of(role), NOW);
    }

    private static OidcUser fresh(String subject, Object groups) {
        var stale = OidcTestFixtures.validUser();
        return new DefaultOidcUser(stale.getAuthorities(), stale.getIdToken(),
                new OidcUserInfo(Map.of("sub", subject, "email", "admin@example.com", "groups", groups)));
    }
}
