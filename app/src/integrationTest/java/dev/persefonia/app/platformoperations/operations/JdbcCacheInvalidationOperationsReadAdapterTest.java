package dev.persefonia.app.platformoperations.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.platformoperations.application.operations.*;
import dev.persefonia.platformoperations.domain.cache.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import dev.persefonia.app.testsupport.SharedPostgresTestServer;

class JdbcCacheInvalidationOperationsReadAdapterTest {
    private static final SharedPostgresTestServer.Database POSTGRES = SharedPostgresTestServer.integrationDatabase();
    private static final Instant NOW = Instant.parse("2026-09-04T12:00:00Z");
    private static JdbcTemplate jdbc;
    private static JdbcCacheInvalidationOperationsReadAdapter reads;

    @BeforeAll
    static void start() {
        DataSource source = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(source);
        var factory = new StaticListableBeanFactory();
        factory.addBean("operationsJdbc", new NamedParameterJdbcTemplate(source));
        reads = new JdbcCacheInvalidationOperationsReadAdapter(
                factory.getBeanProvider(NamedParameterJdbcTemplate.class),
                new CacheInvalidationRecoveryPolicy(Duration.ofMinutes(15)));
    }
    @Test
    void listFiltersOrdersPaginatesAndProjectsAggregatedLatestAttempt() {
        UUID older = insertBatch(CacheInvalidationStatus.FAILED, NOW.minusSeconds(60), null, null,
                CachePurgeFailureReason.TIMEOUT, 2);
        insertTarget(older, "/older", CacheTargetStatus.FAILED);
        insertAttempt(older, 1, CachePurgeProvider.CLOUDFLARE, CachePurgeResult.FAILED,
                CachePurgeFailureReason.TIMEOUT, NOW.minusSeconds(50));
        UUID newer = insertBatch(CacheInvalidationStatus.FAILED, NOW.minusSeconds(30), null, null,
                CachePurgeFailureReason.RATE_LIMITED, 2);
        insertTarget(newer, "/newer-a", CacheTargetStatus.FAILED);
        insertTarget(newer, "/newer-b", CacheTargetStatus.FAILED);
        insertAttempt(newer, 1, CachePurgeProvider.LOCAL, CachePurgeResult.FAILED,
                CachePurgeFailureReason.RATE_LIMITED, NOW.minusSeconds(20));

        var page = reads.search(new CacheInvalidationOperationsSearchRequest(
                CacheInvalidationStatus.FAILED, 1, 25), NOW);

        assertThat(page.totalItems()).isEqualTo(2);
        assertThat(page.items()).extracting(item -> item.id().value()).containsExactly(newer, older);
        assertThat(page.items().getFirst().targetCount()).isEqualTo(2);
        assertThat(page.items().getFirst().attemptCount()).isEqualTo(1);
        assertThat(page.items().getFirst().latestProvider()).isEqualTo(CachePurgeProvider.LOCAL);
        assertThat(page.items().getFirst().latestResult()).isEqualTo(CachePurgeResult.FAILED);
        assertThat(page.items().getFirst().attentionState())
                .isEqualTo(CacheInvalidationAttentionState.RETRY_AVAILABLE);
    }

    @Test
    void summarySeparatesRunningStrandedRetryableExhaustedAndCompleted() {
        seedSimple(CacheInvalidationStatus.REQUESTED, null, null, null, 0, 0);
        seedSimple(CacheInvalidationStatus.RUNNING, NOW.minusSeconds(1), null, null, 1, 0);
        seedSimple(CacheInvalidationStatus.RUNNING, NOW.minusSeconds(900), null, null, 1, 0);
        seedSimple(CacheInvalidationStatus.FAILED, null, null, CachePurgeFailureReason.TIMEOUT, 2, 1);
        seedSimple(CacheInvalidationStatus.PARTIAL, null, NOW.minusSeconds(1), CachePurgeFailureReason.TIMEOUT, 6, 3);
        seedSimple(CacheInvalidationStatus.COMPLETED, null, NOW.minusSeconds(1), null, 2, 1);

        assertThat(reads.summarize(NOW)).isEqualTo(new CacheInvalidationOperationsSummary(1, 1, 1, 1, 1, 1));
    }

    @Test
    void detailUsesSeparateValidatedTargetAndAttemptModelsAndMissingIsEmpty() {
        UUID id = insertBatch(CacheInvalidationStatus.FAILED, NOW.minusSeconds(60), null, null,
                CachePurgeFailureReason.TIMEOUT, 2);
        insertTarget(id, "/detail", CacheTargetStatus.FAILED);
        insertAttempt(id, 1, CachePurgeProvider.CLOUDFLARE, CachePurgeResult.FAILED,
                CachePurgeFailureReason.TIMEOUT, NOW.minusSeconds(30));

        var detail = reads.findById(CacheInvalidationBatchId.from(id), NOW).orElseThrow();
        assertThat(detail.targets()).singleElement().satisfies(target -> {
            assertThat(target.type()).isEqualTo(CacheTargetType.URL);
            assertThat(target.value().value()).isEqualTo("/detail");
        });
        assertThat(detail.attempts()).singleElement().satisfies(attempt ->
                assertThat(attempt.attemptNumber()).isEqualTo(1));
        assertThat(detail.availableAction()).isEqualTo(CacheRecoveryAction.RETRY_FAILED);
        assertThat(reads.findById(CacheInvalidationBatchId.newId(), NOW)).isEmpty();
    }

    @Test
    void structurallyCorruptTargetFailsClosedWithoutEchoingRawValue() {
        UUID id = insertBatch(CacheInvalidationStatus.REQUESTED, NOW, null, null, null, 0);
        String unsafe = "/unsafe%2fvalue";
        insertTarget(id, unsafe, CacheTargetStatus.PENDING);

        assertThatThrownBy(() -> reads.findById(CacheInvalidationBatchId.from(id), NOW))
                .isInstanceOf(OperationsReadException.class)
                .hasMessageNotContaining(unsafe);
    }

    private static void seedSimple(CacheInvalidationStatus status, Instant runningSince, Instant completedAt,
            CachePurgeFailureReason failure, long version, int attempts) {
        UUID id = insertBatch(status, NOW.minusSeconds(3600), runningSince, completedAt, failure, version);
        CacheTargetStatus target = switch (status) {
            case REQUESTED, RUNNING -> CacheTargetStatus.PENDING;
            case COMPLETED -> CacheTargetStatus.PURGED;
            case FAILED -> CacheTargetStatus.FAILED;
            case PARTIAL -> CacheTargetStatus.FAILED;
        };
        insertTarget(id, "/" + id, target);
        for (int number = 1; number <= attempts; number++) {
            boolean success = status == CacheInvalidationStatus.COMPLETED && number == attempts;
            insertAttempt(id, number, CachePurgeProvider.LOCAL,
                    success ? CachePurgeResult.SUCCESS : CachePurgeResult.FAILED,
                    success ? null : CachePurgeFailureReason.TIMEOUT,
                    NOW.minusSeconds(100 - number));
        }
    }

    private static UUID insertBatch(CacheInvalidationStatus status, Instant requestedAt, Instant runningSince,
            Instant completedAt, CachePurgeFailureReason failure, long version) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO operations.cache_invalidation_batches
                    (id, reason, requested_by, requested_at, status, running_since, completed_at, failure_reason, version)
                VALUES (?, 'PUBLIC_RESOURCE_CHANGED', 'SYSTEM', ?, ?, ?, ?, ?, ?)
                """, id, Timestamp.from(requestedAt), status.name(),
                runningSince == null ? null : Timestamp.from(runningSince),
                completedAt == null ? null : Timestamp.from(completedAt),
                failure == null ? null : failure.name(), version);
        return id;
    }

    private static void insertTarget(UUID batch, String value, CacheTargetStatus status) {
        jdbc.update("""
                INSERT INTO operations.cache_invalidation_targets (id, batch_id, target_type, target_value, status)
                VALUES (?, ?, 'URL', ?, ?)
                """, UUID.randomUUID(), batch, value, status.name());
    }

    private static void insertAttempt(UUID batch, int number, CachePurgeProvider provider, CachePurgeResult result,
            CachePurgeFailureReason failure, Instant attemptedAt) {
        jdbc.update("""
                INSERT INTO operations.cache_purge_attempts
                    (id, batch_id, attempt_number, provider, attempted_at, result, failure_reason)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), batch, number, provider.name(), Timestamp.from(attemptedAt), result.name(),
                failure == null ? null : failure.name());
    }
}
