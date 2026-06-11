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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import dev.persefonia.app.identityaccess.bootstrap.AdminBootstrapService;
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
    private AdminBootstrapService bootstrapService;

    @Autowired
    private AdminAccountRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

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
        assertThat(user.adminPrincipal().roles()).extracting(Enum::name).containsExactly("OWNER");
    }

    @Test
    void returnedPersefoniaOidcUserHasRoleAdminAndRoleOwner() {
        assertThat(loadUser("owner-subject", "owner@example.com").getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_OWNER");
    }

    @Test
    void bootstrappedAdminAccountIsPersistedInDatabase() {
        loadUser("owner-subject", "owner@example.com");

        assertThat(countAccounts()).isEqualTo(1);
        assertThat(repository.findByOidcSubject(OidcSubject.of("owner-subject"))).isPresent();
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
    }

    private PersefoniaOidcUser loadUser(String subject, String email) {
        PersefoniaOidcUserService service = new PersefoniaOidcUserService(
                claimMapper,
                bootstrapService,
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
