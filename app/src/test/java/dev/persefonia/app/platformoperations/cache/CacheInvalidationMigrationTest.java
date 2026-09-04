package dev.persefonia.app.platformoperations.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

class CacheInvalidationMigrationTest {
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    private static final Instant NOW = Instant.parse("2026-09-03T10:00:00Z");
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrate() {
        POSTGRES.start();
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .defaultSchema("operations").schemas("operations").createSchemas(true).load().migrate();
    }
    @AfterAll static void stop() { POSTGRES.stop(); }
    @BeforeEach void clear() { jdbc.execute("TRUNCATE operations.cache_invalidation_batches CASCADE"); }

    @Test
    void normalizedTablesColumnsAndChildCascadesExistWithoutProviderSecrets() {
        assertThat(jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'operations' AND table_name LIKE 'cache_%'
                ORDER BY table_name
                """, String.class)).containsExactly("cache_invalidation_batches", "cache_invalidation_targets", "cache_purge_attempts");
        assertThat(columns("cache_invalidation_batches")).containsExactlyInAnyOrder(
                "id", "reason", "requested_by", "requested_at", "status", "running_since",
                "completed_at", "failure_reason", "version");
        assertThat(columns("cache_invalidation_targets")).containsExactlyInAnyOrder(
                "id", "batch_id", "target_type", "target_value", "status");
        assertThat(columns("cache_purge_attempts")).containsExactlyInAnyOrder(
                "id", "batch_id", "attempt_number", "provider", "attempted_at", "result", "failure_reason");
        String allColumns = String.join(" ", columns("cache_invalidation_batches")) + " "
                + String.join(" ", columns("cache_invalidation_targets")) + " "
                + String.join(" ", columns("cache_purge_attempts"));
        assertThat(allColumns).doesNotContain("token", "api_key", "authorization", "zone", "hostname",
                "internal_ip", "response_body", "exception_message", "stack_trace");
        assertThat(jdbc.queryForList("""
                SELECT delete_rule FROM information_schema.referential_constraints
                WHERE constraint_schema = 'operations'
                  AND constraint_name IN ('cache_invalidation_targets_batch_fk', 'cache_purge_attempts_batch_fk')
                ORDER BY constraint_name
                """, String.class)).containsExactly("CASCADE", "CASCADE");

        assertThat(nullableColumns("cache_invalidation_batches"))
                .containsExactlyInAnyOrder("completed_at", "failure_reason", "running_since");
        assertThat(nullableColumns("cache_invalidation_targets")).isEmpty();
        assertThat(nullableColumns("cache_purge_attempts")).containsExactly("failure_reason");
        assertThat(indexes()).contains(
                "cache_invalidation_batches_status_idx", "cache_invalidation_batches_requested_at_idx",
                "cache_invalidation_batches_running_since_running_idx",
                "cache_invalidation_targets_batch_id_idx", "cache_invalidation_targets_status_idx",
                "cache_purge_attempts_batch_id_idx", "cache_purge_attempts_attempted_at_idx",
                "cache_purge_attempts_result_idx");
    }

    @Test
    void rootVocabularyStateAndVersionConstraintsAreEnforced() {
        assertThatThrownBy(() -> insertBatch(UUID.randomUUID(), "CONTENT_CHANGED", "SYSTEM", "REQUESTED", null, null, 0))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertBatch(UUID.randomUUID(), "PUBLIC_RESOURCE_CHANGED", "ADMIN", "REQUESTED", null, null, 0))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertBatch(UUID.randomUUID(), "PUBLIC_RESOURCE_CHANGED", "SYSTEM", "RUNNING", NOW, null, 1))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertBatch(UUID.randomUUID(), "PUBLIC_RESOURCE_CHANGED", "SYSTEM", "COMPLETED", NOW, "TIMEOUT", 2))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertBatch(UUID.randomUUID(), "PUBLIC_RESOURCE_CHANGED", "SYSTEM", "FAILED", null, null, 2))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertBatch(UUID.randomUUID(), "PUBLIC_RESOURCE_CHANGED", "SYSTEM", "REQUESTED", null, null, -1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void runningReservationTimestampMatchesStateAndCannotPrecedeRequest() {
        assertThatThrownBy(() -> insertBatchWithRunning(UUID.randomUUID(), "REQUESTED", NOW, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertBatchWithRunning(UUID.randomUUID(), "RUNNING", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertBatchWithRunning(
                UUID.randomUUID(), "RUNNING", NOW.minusSeconds(1), null))
                .isInstanceOf(DataIntegrityViolationException.class);

        insertBatchWithRunning(UUID.randomUUID(), "RUNNING", NOW, null);
    }

    @Test
    void targetAndAttemptUniquenessRangeVocabularyAndFailureConsistencyAreEnforced() {
        UUID batch = UUID.randomUUID();
        insertBatch(batch, "PUBLIC_RESOURCE_CHANGED", "SYSTEM", "REQUESTED", null, null, 0);
        UUID target = UUID.randomUUID();
        insertTarget(target, batch, "URL", "/example", "PENDING");
        assertThatThrownBy(() -> insertTarget(UUID.randomUUID(), batch, "URL", "/example", "PENDING"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertTarget(UUID.randomUUID(), batch, "GLOBAL", "anything", "PENDING"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertTarget(UUID.randomUUID(), batch, "CACHE_TAG", "a".repeat(129), "PENDING"))
                .isInstanceOf(DataIntegrityViolationException.class);

        insertAttempt(UUID.randomUUID(), batch, 1, "LOCAL", "FAILED", "TIMEOUT");
        assertThatThrownBy(() -> insertAttempt(UUID.randomUUID(), batch, 1, "LOCAL", "FAILED", "TIMEOUT"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertAttempt(UUID.randomUUID(), batch, 4, "LOCAL", "FAILED", "TIMEOUT"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertAttempt(UUID.randomUUID(), batch, 2, "OTHER", "FAILED", "TIMEOUT"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertAttempt(UUID.randomUUID(), batch, 2, "LOCAL", "SUCCESS", "TIMEOUT"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertAttempt(UUID.randomUUID(), batch, 2, "LOCAL", "FAILED", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static List<String> columns(String table) {
        return jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema = 'operations' AND table_name = ?",
                String.class, table);
    }
    private static List<String> nullableColumns(String table) {
        return jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'operations' AND table_name = ? AND is_nullable = 'YES'
                ORDER BY column_name
                """, String.class, table);
    }
    private static List<String> indexes() {
        return jdbc.queryForList("""
                SELECT indexname FROM pg_indexes
                WHERE schemaname = 'operations' AND tablename IN (
                    'cache_invalidation_batches', 'cache_invalidation_targets', 'cache_purge_attempts')
                """, String.class);
    }
    private static void insertBatch(UUID id, String reason, String requestedBy, String status, Instant completedAt,
            String failureReason, long version) {
        jdbc.update("""
                INSERT INTO operations.cache_invalidation_batches
                    (id, reason, requested_by, requested_at, status, completed_at, failure_reason, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, reason, requestedBy, Timestamp.from(NOW), status,
                completedAt == null ? null : Timestamp.from(completedAt), failureReason, version);
    }
    private static void insertTarget(UUID id, UUID batch, String type, String value, String status) {
        jdbc.update("INSERT INTO operations.cache_invalidation_targets (id, batch_id, target_type, target_value, status) VALUES (?, ?, ?, ?, ?)",
                id, batch, type, value, status);
    }
    private static void insertBatchWithRunning(UUID id, String status, Instant runningSince, Instant completedAt) {
        jdbc.update("""
                INSERT INTO operations.cache_invalidation_batches
                    (id, reason, requested_by, requested_at, status, running_since, completed_at, failure_reason, version)
                VALUES (?, 'PUBLIC_RESOURCE_CHANGED', 'SYSTEM', ?, ?, ?, ?, NULL, ?)
                """, id, Timestamp.from(NOW), status,
                runningSince == null ? null : Timestamp.from(runningSince),
                completedAt == null ? null : Timestamp.from(completedAt), status.equals("RUNNING") ? 1 : 0);
    }
    private static void insertAttempt(UUID id, UUID batch, int number, String provider, String result, String failure) {
        jdbc.update("""
                INSERT INTO operations.cache_purge_attempts
                    (id, batch_id, attempt_number, provider, attempted_at, result, failure_reason)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, batch, number, provider, Timestamp.from(NOW), result, failure);
    }
}
