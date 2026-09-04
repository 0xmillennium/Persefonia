package dev.persefonia.app.platformoperations.cache;

import dev.persefonia.platformoperations.domain.cache.CacheInvalidationBatch;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationBatchId;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationBatchRepository;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationStatus;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationTarget;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationTargetId;
import dev.persefonia.platformoperations.domain.cache.CachePurgeAttempt;
import dev.persefonia.platformoperations.domain.cache.CachePurgeAttemptId;
import dev.persefonia.platformoperations.domain.cache.CachePurgeFailureReason;
import dev.persefonia.platformoperations.domain.cache.CachePurgeProvider;
import dev.persefonia.platformoperations.domain.cache.CachePurgeResult;
import dev.persefonia.platformoperations.domain.cache.CacheTargetStatus;
import dev.persefonia.platformoperations.domain.cache.CacheTargetType;
import dev.persefonia.platformoperations.domain.cache.CacheTargetValue;
import dev.persefonia.platformoperations.domain.cache.InvalidationReason;
import dev.persefonia.platformoperations.domain.cache.InvalidationRequester;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class JdbcCacheInvalidationBatchRepositoryAdapter implements CacheInvalidationBatchRepository {
    private static final int MAX_QUERY_LIMIT = 100;
    private final ObjectProvider<NamedParameterJdbcTemplate> jdbc;
    private final ObjectProvider<TransactionTemplate> transactions;

    JdbcCacheInvalidationBatchRepositoryAdapter(
            ObjectProvider<NamedParameterJdbcTemplate> jdbc,
            ObjectProvider<TransactionTemplate> transactions) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    @Override
    public void save(CacheInvalidationBatch batch) {
        Objects.requireNonNull(batch, "batch");
        transactionTemplate().executeWithoutResult(transaction -> {
            Optional<Long> storedVersion = currentVersion(batch.id());
            if (storedVersion.isEmpty()) {
                insertBatch(batch);
                insertTargets(batch);
                insertAttempts(batch.id(), batch.attempts());
                return;
            }
            updateBatch(batch, storedVersion.get());
            updateTargets(batch);
            appendAttempts(batch);
        });
    }

    @Override
    public Optional<CacheInvalidationBatch> findById(CacheInvalidationBatchId id) {
        Objects.requireNonNull(id, "id");
        List<BatchRow> roots = queryRoots("WHERE id = :id", Map.of("id", id.value()));
        return roots.isEmpty() ? Optional.empty() : Optional.of(hydrate(roots).getFirst());
    }

    @Override
    public List<CacheInvalidationBatch> findPendingBatches(int limit) {
        validateLimit(limit);
        return hydrate(queryRoots("""
                WHERE status = 'REQUESTED'
                ORDER BY requested_at ASC, id ASC
                LIMIT :limit
                """, Map.of("limit", limit)));
    }

    @Override
    public List<CacheInvalidationBatch> findRecentFailures(int limit) {
        validateLimit(limit);
        return hydrate(queryRoots("""
                WHERE status IN ('FAILED', 'PARTIAL')
                ORDER BY requested_at DESC, id DESC
                LIMIT :limit
                """, Map.of("limit", limit)));
    }

    private Optional<Long> currentVersion(CacheInvalidationBatchId id) {
        List<Long> versions = jdbc().query("""
                SELECT version FROM operations.cache_invalidation_batches WHERE id = :id
                """, Map.of("id", id.value()), (resultSet, rowNumber) -> resultSet.getLong("version"));
        return versions.stream().findFirst();
    }

    private void insertBatch(CacheInvalidationBatch batch) {
        if (batch.version() != 0) {
            throw new OptimisticLockingFailureException("New cache invalidation batch must have version 0");
        }
        jdbc().update("""
                INSERT INTO operations.cache_invalidation_batches (
                    id, reason, requested_by, requested_at, status, running_since, completed_at, failure_reason, version
                ) VALUES (
                    :id, :reason, :requestedBy, :requestedAt, :status, :runningSince, :completedAt, :failureReason, :version
                )
                """, batchParameters(batch));
    }

    private void updateBatch(CacheInvalidationBatch batch, long storedVersion) {
        long expectedVersion = batch.version() - 1;
        if (storedVersion != expectedVersion) {
            throw stale(batch.id());
        }
        int updated = jdbc().update("""
                UPDATE operations.cache_invalidation_batches
                SET status = :status,
                    running_since = :runningSince,
                    completed_at = :completedAt,
                    failure_reason = :failureReason,
                    version = :version
                WHERE id = :id AND version = :expectedVersion
                """, batchParameters(batch).addValue("expectedVersion", expectedVersion));
        if (updated != 1) {
            throw stale(batch.id());
        }
    }

    private MapSqlParameterSource batchParameters(CacheInvalidationBatch batch) {
        return new MapSqlParameterSource()
                .addValue("id", batch.id().value())
                .addValue("reason", batch.reason().name())
                .addValue("requestedBy", batch.requestedBy().name())
                .addValue("requestedAt", Timestamp.from(batch.requestedAt()))
                .addValue("status", batch.status().name())
                .addValue("runningSince", batch.runningSince().map(Timestamp::from).orElse(null))
                .addValue("completedAt", batch.completedAt().map(Timestamp::from).orElse(null))
                .addValue("failureReason", batch.failureReason().map(Enum::name).orElse(null))
                .addValue("version", batch.version());
    }

    private void insertTargets(CacheInvalidationBatch batch) {
        MapSqlParameterSource[] parameters = batch.targets().stream()
                .map(target -> targetParameters(batch.id(), target))
                .toArray(MapSqlParameterSource[]::new);
        jdbc().batchUpdate("""
                INSERT INTO operations.cache_invalidation_targets (
                    id, batch_id, target_type, target_value, status
                ) VALUES (:id, :batchId, :targetType, :targetValue, :status)
                """, parameters);
    }

    private void updateTargets(CacheInvalidationBatch batch) {
        for (CacheInvalidationTarget target : batch.targets()) {
            int updated = jdbc().update("""
                    UPDATE operations.cache_invalidation_targets
                    SET status = :status
                    WHERE id = :id
                      AND batch_id = :batchId
                      AND target_type = :targetType
                      AND target_value = :targetValue
                    """, targetParameters(batch.id(), target));
            if (updated != 1) {
                throw new CacheInvalidationPersistenceException(
                        "Cache invalidation target membership changed for batch " + batch.id().value());
            }
        }
        Integer storedCount = jdbc().queryForObject("""
                SELECT count(*) FROM operations.cache_invalidation_targets WHERE batch_id = :batchId
                """, Map.of("batchId", batch.id().value()), Integer.class);
        if (storedCount == null || storedCount != batch.targets().size()) {
            throw new CacheInvalidationPersistenceException(
                    "Cache invalidation target count changed for batch " + batch.id().value());
        }
    }

    private MapSqlParameterSource targetParameters(CacheInvalidationBatchId batchId, CacheInvalidationTarget target) {
        return new MapSqlParameterSource()
                .addValue("id", target.id().value())
                .addValue("batchId", batchId.value())
                .addValue("targetType", target.targetType().name())
                .addValue("targetValue", target.value().value())
                .addValue("status", target.status().name());
    }

    private void appendAttempts(CacheInvalidationBatch batch) {
        List<AttemptRow> stored = queryAttemptRows(List.of(batch.id().value()));
        if (stored.size() > batch.attempts().size()) {
            throw new CacheInvalidationPersistenceException("Persisted attempt history is longer than aggregate history");
        }
        for (int index = 0; index < stored.size(); index++) {
            if (!stored.get(index).sameAs(batch.attempts().get(index))) {
                throw new CacheInvalidationPersistenceException("Persisted attempt history is immutable");
            }
        }
        insertAttempts(batch.id(), batch.attempts().subList(stored.size(), batch.attempts().size()));
    }

    private void insertAttempts(CacheInvalidationBatchId batchId, List<CachePurgeAttempt> attempts) {
        for (CachePurgeAttempt attempt : attempts) {
            jdbc().update("""
                    INSERT INTO operations.cache_purge_attempts (
                        id, batch_id, attempt_number, provider, attempted_at, result, failure_reason
                    ) VALUES (
                        :id, :batchId, :attemptNumber, :provider, :attemptedAt, :result, :failureReason
                    )
                    """, new MapSqlParameterSource()
                    .addValue("id", attempt.id().value())
                    .addValue("batchId", batchId.value())
                    .addValue("attemptNumber", attempt.attemptNumber())
                    .addValue("provider", attempt.provider().name())
                    .addValue("attemptedAt", Timestamp.from(attempt.attemptedAt()))
                    .addValue("result", attempt.result().name())
                    .addValue("failureReason", attempt.failureReasonOptional().map(Enum::name).orElse(null)));
        }
    }

    private List<BatchRow> queryRoots(String suffix, Map<String, ?> parameters) {
        return jdbc().query("""
                SELECT id, reason, requested_by, requested_at, status, running_since, completed_at, failure_reason, version
                FROM operations.cache_invalidation_batches
                """ + suffix, parameters, (resultSet, rowNumber) -> new BatchRow(
                resultSet.getObject("id", UUID.class),
                InvalidationReason.valueOf(resultSet.getString("reason")),
                InvalidationRequester.valueOf(resultSet.getString("requested_by")),
                resultSet.getTimestamp("requested_at").toInstant(),
                CacheInvalidationStatus.valueOf(resultSet.getString("status")),
                instantOrNull(resultSet.getTimestamp("running_since")),
                instantOrNull(resultSet.getTimestamp("completed_at")),
                enumOrNull(CachePurgeFailureReason.class, resultSet.getString("failure_reason")),
                resultSet.getLong("version")));
    }

    private List<CacheInvalidationBatch> hydrate(List<BatchRow> roots) {
        if (roots.isEmpty()) return List.of();
        List<UUID> ids = roots.stream().map(BatchRow::id).toList();
        Map<UUID, List<CacheInvalidationTarget>> targets = new HashMap<>();
        for (TargetRow row : queryTargetRows(ids)) {
            targets.computeIfAbsent(row.batchId(), ignored -> new ArrayList<>()).add(row.toDomain());
        }
        Map<UUID, List<CachePurgeAttempt>> attempts = new HashMap<>();
        for (AttemptRow row : queryAttemptRows(ids)) {
            attempts.computeIfAbsent(row.batchId(), ignored -> new ArrayList<>()).add(row.toDomain());
        }
        return roots.stream().map(root -> CacheInvalidationBatch.rehydrate(
                CacheInvalidationBatchId.from(root.id()), root.reason(), root.requestedBy(), root.requestedAt(),
                root.status(), targets.getOrDefault(root.id(), List.of()), attempts.getOrDefault(root.id(), List.of()),
                root.runningSince(), root.completedAt(), root.failureReason(), root.version())).toList();
    }

    private List<TargetRow> queryTargetRows(List<UUID> batchIds) {
        return jdbc().query("""
                SELECT id, batch_id, target_type, target_value, status
                FROM operations.cache_invalidation_targets
                WHERE batch_id IN (:batchIds)
                ORDER BY target_type ASC, target_value ASC, id ASC
                """, Map.of("batchIds", batchIds), (resultSet, rowNumber) -> new TargetRow(
                resultSet.getObject("id", UUID.class), resultSet.getObject("batch_id", UUID.class),
                CacheTargetType.valueOf(resultSet.getString("target_type")), resultSet.getString("target_value"),
                CacheTargetStatus.valueOf(resultSet.getString("status"))));
    }

    private List<AttemptRow> queryAttemptRows(List<UUID> batchIds) {
        return jdbc().query("""
                SELECT id, batch_id, attempt_number, provider, attempted_at, result, failure_reason
                FROM operations.cache_purge_attempts
                WHERE batch_id IN (:batchIds)
                ORDER BY batch_id ASC, attempt_number ASC
                """, Map.of("batchIds", batchIds), (resultSet, rowNumber) -> new AttemptRow(
                resultSet.getObject("id", UUID.class), resultSet.getObject("batch_id", UUID.class),
                resultSet.getInt("attempt_number"), CachePurgeProvider.valueOf(resultSet.getString("provider")),
                resultSet.getTimestamp("attempted_at").toInstant(), CachePurgeResult.valueOf(resultSet.getString("result")),
                enumOrNull(CachePurgeFailureReason.class, resultSet.getString("failure_reason"))));
    }

    private static Instant instantOrNull(Timestamp value) { return value == null ? null : value.toInstant(); }
    private static <E extends Enum<E>> E enumOrNull(Class<E> type, String value) {
        return value == null ? null : Enum.valueOf(type, value);
    }
    private static void validateLimit(int limit) {
        if (limit < 1 || limit > MAX_QUERY_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
    }
    private OptimisticLockingFailureException stale(CacheInvalidationBatchId id) {
        return new OptimisticLockingFailureException("Cache invalidation batch save is stale for id " + id.value());
    }
    private NamedParameterJdbcTemplate jdbc() {
        NamedParameterJdbcTemplate available = jdbc.getIfAvailable();
        if (available == null) throw new CacheInvalidationPersistenceException("JDBC cache repository is unavailable");
        return available;
    }
    private TransactionTemplate transactionTemplate() {
        TransactionTemplate available = transactions.getIfAvailable();
        if (available == null) throw new CacheInvalidationPersistenceException("Cache transaction infrastructure is unavailable");
        return available;
    }

    private record BatchRow(UUID id, InvalidationReason reason, InvalidationRequester requestedBy,
            Instant requestedAt, CacheInvalidationStatus status, Instant runningSince, Instant completedAt,
            CachePurgeFailureReason failureReason, long version) { }
    private record TargetRow(UUID id, UUID batchId, CacheTargetType type, String value, CacheTargetStatus status) {
        CacheInvalidationTarget toDomain() {
            return CacheInvalidationTarget.rehydrate(CacheInvalidationTargetId.from(id), type,
                    CacheTargetValue.of(type, value), status);
        }
    }
    private record AttemptRow(UUID id, UUID batchId, int number, CachePurgeProvider provider,
            Instant attemptedAt, CachePurgeResult result, CachePurgeFailureReason failureReason) {
        CachePurgeAttempt toDomain() {
            return CachePurgeAttempt.rehydrate(CachePurgeAttemptId.from(id), number, provider, attemptedAt,
                    result, failureReason);
        }
        boolean sameAs(CachePurgeAttempt attempt) {
            return id.equals(attempt.id().value()) && number == attempt.attemptNumber()
                    && provider == attempt.provider()
                    && attemptedAt.truncatedTo(ChronoUnit.MICROS)
                            .equals(attempt.attemptedAt().truncatedTo(ChronoUnit.MICROS))
                    && result == attempt.result() && failureReason == attempt.failureReason();
        }
    }
}
