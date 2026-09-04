package dev.persefonia.app.platformoperations.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

class CacheInvalidationV21BackfillMigrationTest {
    @Test
    void v21BackfillsOnlyPreExistingRunningRowsAtMigrationTime() {
        try (var postgres = new PostgreSQLContainer("postgres:17-alpine")) {
            postgres.start();
            var dataSource = new DriverManagerDataSource(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            var jdbc = new JdbcTemplate(dataSource);
            Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .defaultSchema("operations")
                    .schemas("operations")
                    .createSchemas(true)
                    .target("20")
                    .load()
                    .migrate();

            UUID runningId = UUID.randomUUID();
            UUID requestedId = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO operations.cache_invalidation_batches
                        (id, reason, requested_by, requested_at, status, completed_at, failure_reason, version)
                    VALUES (?, 'PUBLIC_RESOURCE_CHANGED', 'SYSTEM', CURRENT_TIMESTAMP - INTERVAL '1 hour',
                            'RUNNING', NULL, NULL, 1)
                    """, runningId);
            jdbc.update("""
                    INSERT INTO operations.cache_invalidation_batches
                        (id, reason, requested_by, requested_at, status, completed_at, failure_reason, version)
                    VALUES (?, 'PUBLIC_RESOURCE_CHANGED', 'SYSTEM', CURRENT_TIMESTAMP - INTERVAL '1 hour',
                            'REQUESTED', NULL, NULL, 0)
                    """, requestedId);
            Instant beforeMigration = jdbc.queryForObject(
                    "SELECT CURRENT_TIMESTAMP", Timestamp.class).toInstant();

            Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .defaultSchema("operations")
                    .schemas("operations")
                    .createSchemas(true)
                    .load()
                    .migrate();

            Instant afterMigration = jdbc.queryForObject(
                    "SELECT CURRENT_TIMESTAMP", Timestamp.class).toInstant();
            Instant runningSince = jdbc.queryForObject("""
                    SELECT running_since FROM operations.cache_invalidation_batches WHERE id = ?
                    """, Timestamp.class, runningId).toInstant();
            Instant requestedAt = jdbc.queryForObject("""
                    SELECT requested_at FROM operations.cache_invalidation_batches WHERE id = ?
                    """, Timestamp.class, runningId).toInstant();

            assertThat(runningSince).isBetween(beforeMigration, afterMigration);
            assertThat(runningSince).isNotEqualTo(requestedAt);
            assertThat(jdbc.queryForObject("""
                    SELECT running_since FROM operations.cache_invalidation_batches WHERE id = ?
                    """, Timestamp.class, requestedId)).isNull();
        }
    }
}
