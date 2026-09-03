package dev.persefonia.app.taxonomy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.taxonomy.application.authorization.TaxonomyCommandActor;
import dev.persefonia.taxonomy.application.command.CreateTagCommand;
import dev.persefonia.taxonomy.application.command.TagCommandResult;
import dev.persefonia.taxonomy.application.service.TagCommandGateway;
import dev.persefonia.taxonomy.domain.port.TagRepository;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
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
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {
        "management.server.port=0",
        "management.health.redis.enabled=false"
})
@ActiveProfiles("test")
class TaxonomyAuditTransactionIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-09-03T12:00:00Z");
    private static final TaxonomyCommandActor OWNER =
            new TaxonomyCommandActor(UUID.randomUUID(), true, true);
    private static final PostgreSQLContainer POSTGRES = postgresContainer();

    static {
        POSTGRES.start();
    }

    @Autowired TagCommandGateway gateway;
    @Autowired TagRepository tags;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void reset() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .defaultSchema("operations")
                .schemas("operations")
                .createSchemas(true)
                .load()
                .migrate();
        jdbc.execute("TRUNCATE taxonomy.tags, discovery.discoverable_resources, audit.audit_records CASCADE");
    }

    @Test
    void successfulCreateCommitsTagAndExactlyOneAuditRecord() {
        TagCommandResult result = gateway.create(command("successful"));

        assertThat(tags.findById(result.tagId())).isPresent();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit.audit_records WHERE action = 'tag.created'", Long.class))
                .isEqualTo(1);
    }

    @Test
    void mandatoryAuditFailureRollsBackTagMutation() {
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
                    jdbc.execute("""
                            ALTER TABLE audit.audit_records
                            ADD CONSTRAINT reject_tag_audit_test CHECK (false)
                            """);
                    gateway.create(command("audit-failure"));
                }))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM taxonomy.tags", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit.audit_records", Long.class)).isZero();
    }

    private static CreateTagCommand command(String suffix) {
        return new CreateTagCommand(OWNER, "Tag " + suffix, "tag-" + suffix, "Description", NOW);
    }

    private static PostgreSQLContainer postgresContainer() {
        PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");
        postgres.withDatabaseName("persefonia_taxonomy_audit");
        postgres.withUsername("persefonia");
        postgres.withPassword("persefonia_dev");
        return postgres;
    }
}
