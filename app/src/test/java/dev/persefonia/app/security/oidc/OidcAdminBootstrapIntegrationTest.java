package dev.persefonia.app.security.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.springframework.transaction.support.TransactionTemplate;

import dev.persefonia.app.identityaccess.bootstrap.TransactionalAdminBootstrapGateway;
import dev.persefonia.identityaccess.domain.admin.AdminAccountRepository;
import dev.persefonia.identityaccess.domain.admin.OidcSubject;

@SpringBootTest(properties = {
        "management.server.port=0",
        "management.health.redis.enabled=false",
        "persefonia.security.admin-access.allowlisted-subjects[0]=owner-subject",
        "persefonia.security.admin-access.allowlisted-subjects[1]=second-subject",
        "persefonia.security.admin-access.automatic-provisioning-enabled=false"
})
@Testcontainers
class OidcAdminBootstrapIntegrationTest {
    private static final String[] MIGRATION_SCHEMAS = {
            "iam",
            "taxonomy",
            "publishing",
            "portfolio",
            "media",
            "communication",
            "discovery",
            "integrity",
            "insights",
            "audit",
            "portability",
            "operations"
    };

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private OidcClaimMapper claimMapper;

    @Autowired
    private TransactionalAdminBootstrapGateway bootstrapGateway;

    @Autowired
    private AdminAccountRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private TransactionTemplate transactions;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.flyway.default-schema", () -> "operations");
        registry.add("spring.flyway.schemas", () -> "operations");
        registry.add("spring.flyway.create-schemas", () -> "true");
    }

    @BeforeEach
    void migrateDatabase() {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .defaultSchema("operations")
                .schemas(MIGRATION_SCHEMAS)
                .createSchemas(true)
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();
    }

    @Test
    void firstAllowlistedOidcUserBootstrapsActiveOwnerThroughRealRepository() {
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
    void unallowlistedOidcUserFailsAndCreatesNoAccount() {
        assertThatThrownBy(() -> loadUser("unallowlisted-subject", "outsider@example.com"))
                .isInstanceOf(OAuth2AuthenticationException.class);

        assertThat(countAccounts()).isZero();
    }

    @Test
    void secondAllowlistedUserIsRejectedWhenAutomaticProvisioningDisabled() {
        loadUser("owner-subject", "owner@example.com");

        assertThatThrownBy(() -> loadUser("second-subject", "second@example.com"))
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
        PersefoniaOidcUserService service = new PersefoniaOidcUserService(
                claimMapper,
                bootstrapGateway,
                request -> OidcTestFixtures.user(Map.of(
                        "sub", subject,
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
