package dev.persefonia.app.profileportfolio.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.profileportfolio.application.authorization.PortfolioCommandActor;
import dev.persefonia.profileportfolio.application.command.CreateProjectCommand;
import dev.persefonia.profileportfolio.application.command.ProjectLocalizationInput;
import dev.persefonia.profileportfolio.application.command.ProjectMutationResult;
import dev.persefonia.profileportfolio.application.service.ProjectCommandGateway;
import dev.persefonia.profileportfolio.domain.project.ProjectId;
import dev.persefonia.profileportfolio.domain.project.ProjectRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import dev.persefonia.app.transaction.PostCommitTaskExecutor;
import dev.persefonia.app.transaction.SpringTransactionSynchronizationPostCommitTaskExecutor;
import dev.persefonia.platformoperations.application.cache.CacheInvalidationExecutionPort;
import dev.persefonia.platformoperations.application.cache.CacheInvalidationRequest;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationBatchId;
import dev.persefonia.app.testsupport.SharedPostgresTestServer;

@SpringBootTest(classes = {dev.persefonia.app.PersefoniaApplication.class,
        ProjectAuditTransactionIntegrationTest.CacheIsolationConfiguration.class}, properties = {
        "management.server.port=0",
        "management.health.redis.enabled=false"
})
@ActiveProfiles("test")
class ProjectAuditTransactionIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-09-03T12:00:00Z");
    private static final PortfolioCommandActor OWNER =
            new PortfolioCommandActor(UUID.randomUUID(), true, true);
    private static final SharedPostgresTestServer.Database POSTGRES = postgresContainer();

    @Autowired ProjectCommandGateway gateway;
    @Autowired ProjectRepository projects;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;
    @Autowired RecordingCacheExecution cacheExecution;
    @Autowired RegistrationControllablePostCommit postCommit;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("site.public-base-url", () -> "https://persefonia.test");
    }

    @BeforeEach
    void reset() {        jdbc.execute("TRUNCATE portfolio.projects, discovery.discoverable_resources, audit.audit_records CASCADE");
        cacheExecution.invocations = 0;
        postCommit.failRegistration = false;
    }

    @Test
    void successfulCreateCommitsProjectDiscoveryAndOneAuditRecord() {
        ProjectMutationResult result = gateway.create(command("successful"));

        assertThat(projects.findById(ProjectId.from(result.projectId()))).isPresent();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM discovery.discoverable_resources WHERE source_entity_id = ?",
                Long.class, result.projectId())).isPositive();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit.audit_records WHERE action = 'project.created'", Long.class))
                .isEqualTo(1);
    }

    @Test
    void mandatoryAuditFailureRollsBackProjectAndDiscovery() {
        UUID[] projectId = new UUID[1];

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
                    jdbc.execute("""
                            ALTER TABLE audit.audit_records
                            ADD CONSTRAINT reject_project_audit_test CHECK (false)
                            """);
                    projectId[0] = gateway.create(command("audit-failure")).projectId();
                }))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM portfolio.projects", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM discovery.discoverable_resources", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit.audit_records", Long.class)).isZero();
        assertThat(cacheExecution.invocations).isZero();
    }

    @Test
    void postCommitRegistrationFailureIsFailOpenAfterRealProjectMutation() {
        postCommit.failRegistration = true;

        ProjectMutationResult result = gateway.create(command("registration-failure"));

        assertThat(projects.findById(ProjectId.from(result.projectId()))).isPresent();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM discovery.discoverable_resources WHERE source_entity_id = ?",
                Long.class, result.projectId())).isPositive();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit.audit_records WHERE action = 'project.created'", Long.class))
                .isEqualTo(1);
        assertThat(cacheExecution.invocations).isZero();
    }

    private static CreateProjectCommand command(String slugSuffix) {
        return new CreateProjectCommand(
                OWNER,
                "ACTIVE",
                "PUBLIC",
                false,
                null,
                Set.of(),
                List.of(
                        new ProjectLocalizationInput(
                                "TR", "proje-" + slugSuffix, "Proje", "Özet", List.of()),
                        new ProjectLocalizationInput(
                                "EN", "project-" + slugSuffix, "Project", "Summary", List.of())),
                List.of(),
                List.of(),
                NOW);
    }

    private static SharedPostgresTestServer.Database postgresContainer() {
        SharedPostgresTestServer.Database postgres = SharedPostgresTestServer.integrationDatabase();
        postgres.withDatabaseName("persefonia_project_audit");
        postgres.withUsername("persefonia");
        postgres.withPassword("persefonia_dev");
        return postgres;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class CacheIsolationConfiguration {
        @Bean @Primary RecordingCacheExecution recordingCacheExecution() { return new RecordingCacheExecution(); }
        @Bean @Primary RegistrationControllablePostCommit registrationControllablePostCommit() {
            return new RegistrationControllablePostCommit();
        }
    }

    static final class RecordingCacheExecution implements CacheInvalidationExecutionPort {
        int invocations;
        @Override public void requestAndExecute(CacheInvalidationRequest request) { invocations++; }
        @Override public void executeInitial(CacheInvalidationBatchId batchId) { }
        @Override public void executeManualRetry(CacheInvalidationBatchId batchId) { }
        @Override public void resumeStranded(CacheInvalidationBatchId batchId) { }
    }

    static final class RegistrationControllablePostCommit implements PostCommitTaskExecutor {
        private final PostCommitTaskExecutor delegate = new SpringTransactionSynchronizationPostCommitTaskExecutor();
        boolean failRegistration;
        @Override public void afterCommit(Runnable task) {
            if (failRegistration) throw new IllegalStateException("forced registration failure");
            delegate.afterCommit(task);
        }
    }
}
