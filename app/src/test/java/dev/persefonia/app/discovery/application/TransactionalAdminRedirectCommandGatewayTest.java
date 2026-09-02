package dev.persefonia.app.discovery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.discovery.application.authorization.AdminRedirectCommandActor;
import dev.persefonia.discovery.application.contract.PublicUrl;
import dev.persefonia.discovery.application.contract.RedirectStatusCode;
import dev.persefonia.discovery.application.redirect.CreateManualRedirectCommand;
import dev.persefonia.discovery.application.redirect.DeactivateManualRedirectCommand;
import dev.persefonia.discovery.application.redirect.DeactivateRedirectRuleResult;
import dev.persefonia.discovery.application.redirect.RedirectRuleCreationResult;
import dev.persefonia.discovery.application.service.AdminRedirectCommandGateway;
import dev.persefonia.discovery.domain.RedirectRuleId;
import dev.persefonia.discovery.domain.RedirectRuleRepository;
import dev.persefonia.identityaccess.application.admin.authorization.AdminCommandAuthorizationException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {
        "management.server.port=0",
        "management.health.redis.enabled=false"
})
@ActiveProfiles("test")
class TransactionalAdminRedirectCommandGatewayTest {
    private static final AdminRedirectCommandActor OWNER = new AdminRedirectCommandActor(
            UUID.fromString("11111111-1111-1111-1111-111111111111"), true, true);
    private static final PostgreSQLContainer POSTGRES = postgresContainer();
    private static boolean migrated;

    static {
        POSTGRES.start();
    }

    @Autowired AdminRedirectCommandGateway gateway;
    @Autowired RedirectRuleRepository redirects;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void resetDatabase() {
        migrateOnce();
        jdbc.execute("TRUNCATE discovery.redirect_rules, discovery.discoverable_resources");
    }

    @Test
    void gatewayImplementsFrameworkFreeContractAndBothMutationsAreTransactionallyProxied() {
        assertThat(gateway).isInstanceOf(AdminRedirectCommandGateway.class);
        assertThat(AopUtils.isAopProxy(gateway)).isTrue();
        assertThat(AopUtils.getTargetClass(gateway)).isEqualTo(TransactionalAdminRedirectCommandGateway.class);
    }

    @Test
    void normalCreateCommitsRedirect() {
        RedirectRuleCreationResult.Created created = create("commit-create");

        assertThat(redirects.findById(created.redirect().redirectRuleId()))
                .hasValueSatisfying(rule -> assertThat(rule.active()).isTrue());
    }

    @Test
    void createInsideOuterRollbackLeavesNoRedirect() {
        RedirectRuleId[] createdId = new RedirectRuleId[1];

        transactions.executeWithoutResult(status -> {
            createdId[0] = create("rollback-create").redirect().redirectRuleId();
            assertThat(redirects.findById(createdId[0])).isPresent();
            status.setRollbackOnly();
        });

        assertThat(redirects.findById(createdId[0])).isEmpty();
    }

    @Test
    void normalDeactivateCommitsInactiveState() {
        RedirectRuleId id = create("commit-deactivate").redirect().redirectRuleId();

        DeactivateRedirectRuleResult result = gateway.deactivate(new DeactivateManualRedirectCommand(OWNER, id));

        assertThat(result).isInstanceOf(DeactivateRedirectRuleResult.Deactivated.class);
        assertThat(redirects.findById(id)).hasValueSatisfying(rule -> assertThat(rule.active()).isFalse());
    }

    @Test
    void deactivateInsideOuterRollbackPreservesActiveState() {
        RedirectRuleId id = create("rollback-deactivate").redirect().redirectRuleId();

        transactions.executeWithoutResult(status -> {
            gateway.deactivate(new DeactivateManualRedirectCommand(OWNER, id));
            assertThat(redirects.findById(id)).hasValueSatisfying(rule -> assertThat(rule.active()).isFalse());
            status.setRollbackOnly();
        });

        assertThat(redirects.findById(id)).hasValueSatisfying(rule -> assertThat(rule.active()).isTrue());
    }

    @Test
    void ownerAuthorizationOccursBeforeMutation() {
        AdminRedirectCommandActor editor = new AdminRedirectCommandActor(UUID.randomUUID(), true, false);

        assertThatThrownBy(() -> gateway.create(command("unauthorized", editor)))
                .isInstanceOf(AdminCommandAuthorizationException.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM discovery.redirect_rules", Long.class)).isZero();
    }

    @Test
    void unexpectedPersistenceFailurePropagatesInsteadOfBecomingTypedRejection() {
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
                    jdbc.execute("ALTER TABLE discovery.redirect_rules ADD CONSTRAINT reject_test_insert CHECK (false)");
                    gateway.create(command("persistence-failure", OWNER));
                }))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM discovery.redirect_rules", Long.class)).isZero();
    }

    private RedirectRuleCreationResult.Created create(String key) {
        return (RedirectRuleCreationResult.Created) gateway.create(command(key, OWNER));
    }

    private static CreateManualRedirectCommand command(String key, AdminRedirectCommandActor actor) {
        return new CreateManualRedirectCommand(
                actor,
                new PublicUrl("/old-" + key),
                new PublicUrl("/new-" + key),
                RedirectStatusCode.MOVED_PERMANENTLY_301);
    }

    private static synchronized void migrateOnce() {
        if (migrated) {
            return;
        }
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .defaultSchema("operations")
                .schemas("operations")
                .createSchemas(true)
                .load()
                .migrate();
        migrated = true;
    }

    private static PostgreSQLContainer postgresContainer() {
        PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");
        postgres.withDatabaseName("persefonia_redirect_command_gateway");
        postgres.withUsername("persefonia");
        postgres.withPassword("persefonia_dev");
        return postgres;
    }
}
