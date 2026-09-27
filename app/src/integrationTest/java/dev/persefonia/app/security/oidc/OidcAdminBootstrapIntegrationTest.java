package dev.persefonia.app.security.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import dev.persefonia.app.testsupport.SharedPostgresTestServer;
import org.springframework.transaction.support.TransactionTemplate;

import dev.persefonia.app.identityaccess.bootstrap.TransactionalAdminBootstrapGateway;
import dev.persefonia.identityaccess.domain.admin.AdminAccountRepository;
import dev.persefonia.identityaccess.domain.admin.OidcSubject;

@SpringBootTest(properties = {
        "management.server.port=0",
        "management.health.redis.enabled=false",
        "persefonia.security.admin-access.required-oidc-group=admin",
        "persefonia.security.admin-access.initial-owner-bootstrap-enabled=true",
        "persefonia.security.admin-access.automatic-provisioning-enabled=true"
})
class OidcAdminBootstrapIntegrationTest {
    private static final SharedPostgresTestServer.Database POSTGRES = SharedPostgresTestServer.integrationDatabase();

    @Autowired
    private OidcClaimMapper claimMapper;

    @Autowired
    private TransactionalAdminBootstrapGateway bootstrapGateway;

    @Autowired
    private AdminAccountRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactions;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void firstGroupAdmittedOidcUserBootstrapsActiveOwnerThroughRealRepository() {
        PersefoniaOidcUser user = loadUser("owner-subject", "owner@example.com");

        assertThat(user.adminPrincipal().status().name()).isEqualTo("ACTIVE");
        assertThat(user.adminPrincipal().roles()).extracting(value -> value.name()).containsExactly("OWNER");
    }

    @Test
    void returnedPersefoniaOidcUserHasRoleAdminAndRoleOwner() {
        assertThat(loadUser("owner-subject", "owner@example.com").getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_OWNER");
    }

    @Test
    void bootstrappedAdminAccountIsPersistedInDatabase() {
        loadUser("owner-subject", "owner@example.com");

        assertThat(countAccounts()).isEqualTo(1);
        assertThat(repository.findByOidcSubject(OidcSubject.of("owner-subject"))).isPresent();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit.audit_records WHERE action = 'admin_account.bootstrapped'",
                Long.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit.audit_record_metadata m JOIN audit.audit_records r "
                        + "ON r.id = m.audit_record_id WHERE r.action = 'admin_account.bootstrapped' "
                        + "AND m.metadata_key = 'bootstrap_outcome' "
                        + "AND m.metadata_value = 'INITIAL_OWNER_BOOTSTRAPPED'",
                Long.class)).isEqualTo(1);
    }

    @Test
    void nonAdminGroupOidcUserFailsAndCreatesNoAccount() {
        assertThatThrownBy(() -> loadUser("outsider-subject", "outsider@example.com", List.of("user")))
                .isInstanceOf(OAuth2AuthenticationException.class);

        assertThat(countAccounts()).isZero();
    }

    @Test
    void secondEligibleIdentityIsPersistedAsEditorWithProvisioningAudit() {
        loadUser("owner-subject", "owner@example.com");
        var editor = loadUser("second-subject", "second@example.com", List.of("admin", "owner"));
        assertThat(editor.adminPrincipal().roles()).extracting(role -> role.name()).containsExactly("EDITOR");
        assertThat(countAccounts()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM audit.audit_record_metadata "
                + "WHERE metadata_key = 'bootstrap_outcome' AND metadata_value = 'AUTOMATICALLY_PROVISIONED'",
                Long.class)).isEqualTo(1);
    }

    @Test
    void existingOwnerLosingRequiredGroupCannotLoginOrMutateLoginState() {
        loadUser("owner-subject", "owner@example.com");
        Instant previousLogin = lastLoginAt("owner-subject");
        Long previousVersion = version("owner-subject");
        assertThatThrownBy(() -> loadUser("owner-subject", "owner@example.com", List.of("user")))
                .isInstanceOf(OAuth2AuthenticationException.class);
        assertThat(lastLoginAt("owner-subject")).isEqualTo(previousLogin);
        assertThat(version("owner-subject")).isEqualTo(previousVersion);
    }

    @Test
    void disabledEligibleAccountAndNormalizedEmailCollisionRemainDenied() {
        loadUser("owner-subject", "owner@example.com");
        assertThatThrownBy(() -> loadUser("different-subject", "OWNER@example.com"))
                .isInstanceOf(OAuth2AuthenticationException.class);
        transactions.executeWithoutResult(status -> {
            var account = repository.findByOidcSubject(OidcSubject.of("owner-subject")).orElseThrow();
            repository.save(account.disable(Instant.now()));
        });
        assertThatThrownBy(() -> loadUser("owner-subject", "owner@example.com"))
                .isInstanceOf(OAuth2AuthenticationException.class);
        assertThat(countAccounts()).isEqualTo(1);
    }

    @Test
    void existingActiveAdminLogsInAndUpdatesLastLoginAt() {
        loadUser("owner-subject", "owner@example.com");
        Instant firstLogin = lastLoginAt("owner-subject");

        loadUser("owner-subject", "owner@example.com");

        assertThat(lastLoginAt("owner-subject")).isAfterOrEqualTo(firstLogin);
        assertThat(version("owner-subject")).isGreaterThan(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM audit.audit_records", Long.class))
                .isEqualTo(1);
    }

    @Test
    void mandatoryAuditFailureRollsBackNewAdminProvisioning() {
        var claims = claimMapper.toAdminIdentityClaims(OidcTestFixtures.user(Map.of(
                "sub", "owner-subject",
                "email", "owner@example.com",
                "name", "Admin",
                "email_verified", true)));

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
                    jdbcTemplate.execute("""
                            ALTER TABLE audit.audit_records
                            ADD CONSTRAINT reject_bootstrap_audit_test CHECK (false)
                            """);
                    bootstrapGateway.resolveOrBootstrap(claims);
                }))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(countAccounts()).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM audit.audit_records", Long.class)).isZero();
    }

    private PersefoniaOidcUser loadUser(String subject, String email) {
        return loadUser(subject, email, List.of("admin"));
    }

    private PersefoniaOidcUser loadUser(String subject, String email, List<String> groups) {
        PersefoniaOidcUserService service = new PersefoniaOidcUserService(
                claimMapper,
                bootstrapGateway,
                request -> OidcTestFixtures.user(Map.of(
                        "sub", subject,
                        "groups", groups,
                        "email", email,
                        "name", "Admin",
                        "email_verified", true)));
        return (PersefoniaOidcUser) service.loadUser(null);
    }

    private Long countAccounts() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM iam.admin_accounts", Long.class);
    }

    private Instant lastLoginAt(String subject) {
        return jdbcTemplate.queryForObject(
                "SELECT last_login_at FROM iam.admin_accounts WHERE oidc_subject = ?",
                (resultSet, rowNumber) -> resultSet.getTimestamp(1).toInstant(),
                subject);
    }

    private Long version(String subject) {
        return jdbcTemplate.queryForObject(
                "SELECT version FROM iam.admin_accounts WHERE oidc_subject = ?",
                Long.class,
                subject);
    }
}
