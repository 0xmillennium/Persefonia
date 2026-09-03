package dev.persefonia.app.platformoperations.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.platformoperations.domain.cache.CacheInvalidationBatch;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationBatchId;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationStatus;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationTarget;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationTargetId;
import dev.persefonia.platformoperations.domain.cache.CachePurgeFailureReason;
import dev.persefonia.platformoperations.domain.cache.CachePurgeProvider;
import dev.persefonia.platformoperations.domain.cache.CachePurgeResult;
import dev.persefonia.platformoperations.domain.cache.CacheTargetOutcome;
import dev.persefonia.platformoperations.domain.cache.CacheTargetStatus;
import dev.persefonia.platformoperations.domain.cache.CacheTargetType;
import dev.persefonia.platformoperations.domain.cache.CacheTargetValue;
import dev.persefonia.platformoperations.domain.cache.InvalidationReason;
import dev.persefonia.platformoperations.domain.cache.InvalidationRequester;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

class JdbcCacheInvalidationBatchRepositoryAdapterTest {
    private static final Instant BASE = Instant.parse("2026-09-03T10:00:00Z");
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    private static JdbcTemplate jdbc;
    private static JdbcCacheInvalidationBatchRepositoryAdapter repository;

    @BeforeAll
    static void migrate() {
        POSTGRES.start();
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .defaultSchema("operations").schemas("operations").createSchemas(true).load().migrate();
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("jdbc", new NamedParameterJdbcTemplate(dataSource));
        beans.addBean("transactions", new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
        repository = new JdbcCacheInvalidationBatchRepositoryAdapter(
                beans.getBeanProvider(NamedParameterJdbcTemplate.class), beans.getBeanProvider(TransactionTemplate.class));
    }

    @AfterAll static void stop() { POSTGRES.stop(); }

    @BeforeEach
    void clear() {
        jdbc.execute("TRUNCATE operations.cache_invalidation_batches CASCADE");
    }

    @Test
    void requestedBatchRoundTripsWithStableIdentityAndCompleteState() {
        CacheInvalidationBatch original = requested(BASE, "/articles/example");
        repository.save(original);
        CacheInvalidationBatch loaded = repository.findById(original.id()).orElseThrow();

        assertBatchState(loaded, original);
        assertThat(loaded.status()).isEqualTo(CacheInvalidationStatus.REQUESTED);
        assertThat(loaded.targets()).hasSize(2).allMatch(target -> target.status() == CacheTargetStatus.PENDING);
        assertThat(loaded.attempts()).isEmpty();
    }

    @Test
    void lifecycleAndRetriesRoundTripWhileTargetsStayStableAndAttemptsAppendOnly() {
        CacheInvalidationBatch batch = requested(BASE, "/articles/retry");
        List<CacheInvalidationTargetId> targetIds = batch.targets().stream().map(CacheInvalidationTarget::id).toList();
        repository.save(batch);

        batch.beginInitialAttempt();
        repository.save(batch);
        assertThat(repository.findById(batch.id()).orElseThrow().status()).isEqualTo(CacheInvalidationStatus.RUNNING);

        failPending(batch, 1, BASE.plusSeconds(10));
        repository.save(batch);
        List<List<Object>> firstAttempt = attemptRows(batch.id());
        for (int attempt = 2; attempt <= 3; attempt++) {
            batch.beginManualRetry();
            repository.save(batch);
            failPending(batch, attempt, BASE.plusSeconds(attempt * 10L));
            repository.save(batch);
        }

        CacheInvalidationBatch loaded = repository.findById(batch.id()).orElseThrow();
        assertThat(loaded.targets()).extracting(CacheInvalidationTarget::id).containsExactlyInAnyOrderElementsOf(targetIds);
        assertThat(loaded.attempts()).extracting(attempt -> attempt.attemptNumber()).containsExactly(1, 2, 3);
        assertThat(loaded.completedAt()).isPresent();
        assertThat(attemptRows(batch.id()).getFirst()).isEqualTo(firstAttempt.getFirst());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM operations.cache_purge_attempts WHERE batch_id = ?",
                Integer.class, batch.id().value())).isEqualTo(3);
    }

    @Test
    void staleRootFailsBeforeAnyChildStateCanSurvive() {
        CacheInvalidationBatch original = requested(BASE, "/articles/stale");
        repository.save(original);
        CacheInvalidationBatch first = repository.findById(original.id()).orElseThrow();
        CacheInvalidationBatch stale = repository.findById(original.id()).orElseThrow();
        first.beginInitialAttempt();
        repository.save(first);
        stale.beginInitialAttempt();

        assertThatThrownBy(() -> repository.save(stale)).isInstanceOf(OptimisticLockingFailureException.class);
        CacheInvalidationBatch loaded = repository.findById(original.id()).orElseThrow();
        assertThat(loaded.version()).isEqualTo(1);
        assertThat(loaded.targets()).allMatch(target -> target.status() == CacheTargetStatus.PENDING);
        assertThat(loaded.attempts()).isEmpty();
    }

    @Test
    void childPersistenceFailureRollsBackRootTargetsAndAttemptTogether() {
        CacheInvalidationBatch batch = requested(BASE, "/articles/atomic");
        repository.save(batch);
        batch.beginInitialAttempt();
        repository.save(batch);
        CacheInvalidationBatch running = repository.findById(batch.id()).orElseThrow();
        failPending(running, 1, BASE.plusSeconds(10));
        jdbc.execute("ALTER TABLE operations.cache_invalidation_targets ADD CONSTRAINT test_no_failed CHECK (status <> 'FAILED')");
        try {
            assertThatThrownBy(() -> repository.save(running)).isInstanceOf(DataIntegrityViolationException.class);
            CacheInvalidationBatch loaded = repository.findById(batch.id()).orElseThrow();
            assertThat(loaded.status()).isEqualTo(CacheInvalidationStatus.RUNNING);
            assertThat(loaded.version()).isEqualTo(1);
            assertThat(loaded.targets()).allMatch(target -> target.status() == CacheTargetStatus.PENDING);
            assertThat(loaded.attempts()).isEmpty();
        } finally {
            jdbc.execute("ALTER TABLE operations.cache_invalidation_targets DROP CONSTRAINT test_no_failed");
        }
    }

    @Test
    void boundedQueriesApplyStatusOrderingLimitsAndHydrateChildrenInBulk() {
        CacheInvalidationBatch older = requested(BASE, "/older");
        CacheInvalidationBatch newer = requested(BASE.plusSeconds(1), "/newer");
        CacheInvalidationBatch failure = requested(BASE.plusSeconds(2), "/failure");
        CacheInvalidationBatch partial = requested(BASE.plusSeconds(3), "/partial");
        repository.save(newer);
        repository.save(older);
        repository.save(failure);
        repository.save(partial);
        failure.beginInitialAttempt();
        repository.save(failure);
        failPending(failure, 1, BASE.plusSeconds(20));
        repository.save(failure);
        partial.beginInitialAttempt();
        repository.save(partial);
        partial.recordAttemptResult(1, CachePurgeProvider.CLOUDFLARE, BASE.plusSeconds(30), CachePurgeResult.FAILED,
                CachePurgeFailureReason.NETWORK_ERROR,
                List.of(CacheTargetOutcome.of(partial.targets().get(0).id(), CacheTargetStatus.PURGED),
                        CacheTargetOutcome.of(partial.targets().get(1).id(), CacheTargetStatus.FAILED)),
                BASE.plusSeconds(31));
        repository.save(partial);

        assertThat(repository.findPendingBatches(2)).extracting(CacheInvalidationBatch::id)
                .containsExactly(older.id(), newer.id());
        assertThat(repository.findRecentFailures(2)).extracting(CacheInvalidationBatch::id)
                .containsExactly(partial.id(), failure.id());
        assertThat(repository.findRecentFailures(2)).allMatch(item -> item.targets().size() == 2 && item.attempts().size() == 1);
        assertThatThrownBy(() -> repository.findPendingBatches(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.findRecentFailures(101)).isInstanceOf(IllegalArgumentException.class);
    }

    private static CacheInvalidationBatch requested(Instant requestedAt, String url) {
        return CacheInvalidationBatch.request(CacheInvalidationBatchId.newId(), InvalidationReason.PUBLIC_RESOURCE_CHANGED,
                InvalidationRequester.SYSTEM, requestedAt,
                List.of(CacheInvalidationTarget.pending(CacheInvalidationTargetId.newId(), CacheTargetType.URL,
                                CacheTargetValue.url(url)),
                        CacheInvalidationTarget.pending(CacheInvalidationTargetId.newId(), CacheTargetType.CACHE_TAG,
                                CacheTargetValue.cacheTag("site:public-documents"))));
    }
    private static void failPending(CacheInvalidationBatch batch, int attemptNumber, Instant attemptedAt) {
        batch.recordAttemptResult(attemptNumber, CachePurgeProvider.CLOUDFLARE, attemptedAt, CachePurgeResult.FAILED,
                CachePurgeFailureReason.TIMEOUT,
                batch.targets().stream().filter(target -> target.status() == CacheTargetStatus.PENDING)
                        .map(target -> CacheTargetOutcome.of(target.id(), CacheTargetStatus.FAILED)).toList(),
                attemptedAt.plusSeconds(1));
    }
    private static List<List<Object>> attemptRows(CacheInvalidationBatchId id) {
        return jdbc.query("""
                SELECT id, attempt_number, provider, attempted_at, result, failure_reason
                FROM operations.cache_purge_attempts WHERE batch_id = ? ORDER BY attempt_number
                """, (rs, row) -> List.of(rs.getObject(1), rs.getInt(2), rs.getString(3), rs.getTimestamp(4),
                rs.getString(5), rs.getString(6)), id.value());
    }
    private static void assertBatchState(CacheInvalidationBatch actual, CacheInvalidationBatch expected) {
        assertThat(actual.id()).isEqualTo(expected.id());
        assertThat(actual.reason()).isEqualTo(expected.reason());
        assertThat(actual.requestedBy()).isEqualTo(expected.requestedBy());
        assertThat(actual.requestedAt()).isEqualTo(expected.requestedAt());
        assertThat(actual.status()).isEqualTo(expected.status());
        assertThat(actual.version()).isEqualTo(expected.version());
        assertThat(actual.completedAt()).isEqualTo(expected.completedAt());
        assertThat(actual.failureReason()).isEqualTo(expected.failureReason());
        assertThat(actual.targets()).usingRecursiveFieldByFieldElementComparator()
                .containsExactlyInAnyOrderElementsOf(expected.targets());
        assertThat(actual.attempts()).isEqualTo(expected.attempts());
    }
}
