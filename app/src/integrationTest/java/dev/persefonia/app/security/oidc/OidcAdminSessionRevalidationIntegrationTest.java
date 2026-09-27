package dev.persefonia.app.security.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import dev.persefonia.app.identityaccess.bootstrap.TransactionalAdminBootstrapGateway;
import dev.persefonia.app.identityaccess.session.TransactionalAdminSessionRevalidationGateway;
import dev.persefonia.app.security.admin.AdminPrincipal;
import dev.persefonia.app.testsupport.SharedPostgresTestServer;
import dev.persefonia.identityaccess.domain.admin.AdminAccountRepository;
import dev.persefonia.identityaccess.domain.admin.OidcSubject;

@SpringBootTest(properties = {
        "management.server.port=0",
        "management.health.redis.enabled=false",
        "persefonia.security.admin-access.required-oidc-group=admin",
        "persefonia.security.admin-access.initial-owner-bootstrap-enabled=true",
        "persefonia.security.admin-access.automatic-provisioning-enabled=true"
})
class OidcAdminSessionRevalidationIntegrationTest {
    private static final SharedPostgresTestServer.Database POSTGRES = SharedPostgresTestServer.integrationDatabase();
    @Autowired
    private TransactionalAdminBootstrapGateway bootstrap;
    @Autowired
    private TransactionalAdminSessionRevalidationGateway gateway;
    @Autowired
    private OidcClaimMapper mapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AdminAccountRepository repository;
    @Autowired
    private TransactionTemplate transactions;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void databaseRoleChangesAreReloadedWithoutMutationLoginUpdateOrAudit() {
        var user = provision();
        Map<String, Object> before = row();
        Long auditBefore = jdbc.queryForObject("SELECT count(*) FROM audit.audit_records", Long.class);
        for (String role : List.of("EDITOR", "OWNER")) {
            jdbc.update("UPDATE iam.admin_account_roles SET role = ? WHERE admin_account_id = ?",
                    role, user.adminPrincipal().accountId().value());
            var refreshed = revalidate(user, List.of("admin", "owner"));
            assertThat(refreshed.getAuthorities()).extracting(authority -> authority.getAuthority())
                    .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_" + role);
            assertThat(row()).isEqualTo(before);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM audit.audit_records", Long.class)).isEqualTo(auditBefore);
        }
    }

    @Test
    void groupRemovalDisabledAndMissingLocalAccountsRevokeWithoutReprovisioning() {
        var user = provision();
        Map<String, Object> before = row();
        assertRevoked(user, List.of("user"));
        assertThat(row()).isEqualTo(before);
        transactions.executeWithoutResult(status -> repository.save(repository.findByOidcSubject(
                OidcSubject.of("opaque-subject")).orElseThrow().disable(Instant.now())));
        Map<String, Object> disabled = row();
        assertRevoked(user, List.of("admin"));
        assertThat(row()).isEqualTo(disabled);
        jdbc.update("DELETE FROM iam.admin_accounts WHERE oidc_subject = ?", "opaque-subject");
        assertRevoked(user, List.of("admin"));
        assertThat(repository.countAll()).isZero();
    }

    @Test
    void missingFreshSubjectWithAdminGroupBlocksSessionDespiteValidStaleIdToken() throws Exception {
        assertInvalidFreshIdentityBlocksSession(Map.of("groups", List.of("admin")));
    }

    @Test
    void differentFreshSubjectWithAdminGroupBlocksTheOriginalAuthenticatedSession() throws Exception {
        assertInvalidFreshIdentityBlocksSession(Map.of("sub", "different-subject", "groups", List.of("admin")));
    }

    private void assertInvalidFreshIdentityBlocksSession(Map<String, Object> freshClaims) throws Exception {
        var user = provision();
        Map<String, Object> before = row();
        Long auditBefore = jdbc.queryForObject("SELECT count(*) FROM audit.audit_records", Long.class);
        var manager = mock(OAuth2AuthorizedClientManager.class);
        Instant now = Instant.parse("2026-09-26T12:00:00Z");
        var authentication = new OAuth2AuthenticationToken(user, user.getAuthorities(), "authelia");
        when(manager.authorize(any())).thenReturn(new OAuth2AuthorizedClient(
                AdminOidcSessionRevalidationServiceTest.registration(), user.getName(),
                new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "fake-access-token", now, now.plusSeconds(3600)),
                new OAuth2RefreshToken("fake-refresh-token", now)));
        // The external boundary returns UserInfo; the real freshness guard, mapper, gateway and filter run below.
        var freshUser = new DefaultOidcUser(user.getAuthorities(), user.getIdToken(), new OidcUserInfo(freshClaims));
        var service = new AdminOidcSessionRevalidationService(manager,
                new AdminOidcFreshUserService(ignored -> freshUser), mapper, gateway);
        var request = new MockHttpServletRequest("GET", "/admin");
        var response = new MockHttpServletResponse();
        var session = spy(new MockHttpSession());
        request.setSession(session);
        var contexts = new DelegatingSecurityContextRepository(new RequestAttributeSecurityContextRepository(),
                new HttpSessionSecurityContextRepository());
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        Instant previous = now.minusSeconds(301);
        AdminOidcSessionState.markSuccessfulRevalidation(request, previous);
        var chain = mock(FilterChain.class);

        assertThat(user.getIdToken().getSubject()).isEqualTo("opaque-subject");
        assertThatThrownBy(() -> service.revalidate(authentication, user, request, response))
                .isInstanceOfSatisfying(AdminOidcSessionRevalidationException.class, exception ->
                        assertThat(exception.reason()).isEqualTo(AdminOidcSessionFailureReason.INVALID_PROVIDER_RESPONSE));
        new AdminOidcSessionRevalidationFilter(new AdminOidcSessionProperties(), service,
                Clock.fixed(now, ZoneOffset.UTC), contexts).doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(session.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(contexts.loadDeferredContext(request).get().getAuthentication()).isNull();
        verifyNoInteractions(chain);
        var timestamp = ArgumentCaptor.forClass(Object.class);
        verify(session).setAttribute(eq("dev.persefonia.security.oidc.last-successful-revalidation"), timestamp.capture());
        assertThat(timestamp.getValue()).isEqualTo(previous);
        assertThat(row()).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit.audit_records", Long.class)).isEqualTo(auditBefore);
    }

    private void assertRevoked(PersefoniaOidcUser user, List<String> groups) {
        assertThatThrownBy(() -> revalidate(user, groups)).isInstanceOfSatisfying(
                AdminOidcSessionRevalidationException.class,
                exception -> assertThat(exception.reason()).isEqualTo(AdminOidcSessionFailureReason.ACCESS_REVOKED));
    }

    private PersefoniaOidcUser provision() {
        var oidc = OidcTestFixtures.validUser();
        return new PersefoniaOidcUser(oidc, AdminPrincipal.from(bootstrap.resolveOrBootstrap(
                mapper.toAdminIdentityClaims(oidc)).account()));
    }

    private PersefoniaOidcUser revalidate(PersefoniaOidcUser user, List<String> groups) {
        var manager = mock(OAuth2AuthorizedClientManager.class);
        var fresh = mock(AdminOidcFreshUserService.class);
        Instant now = Instant.now();
        when(manager.authorize(any())).thenReturn(new OAuth2AuthorizedClient(
                AdminOidcSessionRevalidationServiceTest.registration(), user.getName(),
                new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "fake-access-token", now, now.plusSeconds(3600)),
                new OAuth2RefreshToken("fake-refresh-token", now)));
        when(fresh.loadFreshUser(any(), any(), any())).thenReturn(new DefaultOidcUser(user.getAuthorities(),
                user.getIdToken(), new OidcUserInfo(Map.of("sub", "opaque-subject", "email", "admin@example.com", "groups", groups))));
        return new AdminOidcSessionRevalidationService(manager, fresh, mapper, gateway).revalidate(
                new OAuth2AuthenticationToken(user, user.getAuthorities(), "authelia"), user,
                new MockHttpServletRequest(), new MockHttpServletResponse());
    }

    private Map<String, Object> row() {
        return jdbc.queryForMap("SELECT * FROM iam.admin_accounts WHERE oidc_subject = ?", "opaque-subject");
    }
}
