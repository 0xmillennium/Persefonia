package dev.persefonia.app.platformoperations.operations;

import dev.persefonia.platformoperations.application.operations.*;
import dev.persefonia.platformoperations.domain.cache.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCacheInvalidationOperationsReadAdapter implements CacheInvalidationOperationsReadPort {
    private final ObjectProvider<NamedParameterJdbcTemplate> jdbc;
    private final CacheInvalidationRecoveryPolicy recoveryPolicy;

    public JdbcCacheInvalidationOperationsReadAdapter(
            ObjectProvider<NamedParameterJdbcTemplate> jdbc,
            CacheInvalidationRecoveryPolicy recoveryPolicy) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.recoveryPolicy = Objects.requireNonNull(recoveryPolicy, "recoveryPolicy");
    }

    @Override
    public CacheInvalidationOperationsListPage search(
            CacheInvalidationOperationsSearchRequest request, Instant now) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(now, "now");
        try {
            String filter = request.status() == null ? "" : "WHERE batch.status = :status";
            MapSqlParameterSource parameters = new MapSqlParameterSource()
                    .addValue("status", request.status() == null ? null : request.status().name())
                    .addValue("limit", request.pageSize())
                    .addValue("offset", (long) (request.page() - 1) * request.pageSize());
            Long total = jdbc().queryForObject(
                    "SELECT count(*) FROM operations.cache_invalidation_batches batch " + filter,
                    parameters, Long.class);
            List<CacheInvalidationOperationsListItem> items = jdbc().query("""
                    SELECT batch.id, batch.requested_at, batch.status, batch.running_since, batch.completed_at,
                           (SELECT count(*) FROM operations.cache_invalidation_targets target
                            WHERE target.batch_id = batch.id) AS target_count,
                           (SELECT count(*) FROM operations.cache_purge_attempts attempt_count
                            WHERE attempt_count.batch_id = batch.id) AS attempt_count,
                           latest.provider, latest.result, latest.failure_reason
                    FROM operations.cache_invalidation_batches batch
                    LEFT JOIN LATERAL (
                        SELECT attempt.provider, attempt.result, attempt.failure_reason
                        FROM operations.cache_purge_attempts attempt
                        WHERE attempt.batch_id = batch.id
                        ORDER BY attempt.attempt_number DESC
                        LIMIT 1
                    ) latest ON true
                    """ + filter + "\n" + """
                    ORDER BY batch.requested_at DESC, batch.id DESC
                    LIMIT :limit OFFSET :offset
                    """, parameters, (rs, row) -> mapListItem(rs, now));
            return new CacheInvalidationOperationsListPage(items, total == null ? 0 : total,
                    request.page(), request.pageSize());
        } catch (OperationsReadException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new OperationsReadException();
        }
    }

    @Override
    public Optional<CacheInvalidationOperationsDetail> findById(CacheInvalidationBatchId id, Instant now) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(now, "now");
        try {
            List<DetailRoot> roots = jdbc().query("""
                    SELECT id, reason, requested_by, requested_at, status, running_since,
                           completed_at, failure_reason, version
                    FROM operations.cache_invalidation_batches
                    WHERE id = :id
                    """, Map.of("id", id.value()), (rs, row) -> mapRoot(rs));
            if (roots.isEmpty()) return Optional.empty();
            List<CacheInvalidationOperationsTarget> targets = jdbc().query("""
                    SELECT target_type, target_value, status
                    FROM operations.cache_invalidation_targets
                    WHERE batch_id = :id
                    ORDER BY target_type, target_value, id
                    """, Map.of("id", id.value()), (rs, row) -> mapTarget(rs));
            List<CacheInvalidationOperationsAttempt> attempts = jdbc().query("""
                    SELECT attempt_number, provider, attempted_at, result, failure_reason
                    FROM operations.cache_purge_attempts
                    WHERE batch_id = :id
                    ORDER BY attempt_number
                    """, Map.of("id", id.value()), (rs, row) -> mapAttempt(rs));
            DetailRoot root = roots.getFirst();
            CacheRecoveryAction action = recoveryPolicy.action(root.status(), root.runningSince(),
                    root.completedAt(), attempts.size(), now);
            return Optional.of(new CacheInvalidationOperationsDetail(
                    CacheInvalidationBatchId.from(root.id()), root.reason(), root.requestedBy(), root.requestedAt(),
                    root.status(), root.runningSince(), root.completedAt(), root.failureReason(), root.version(),
                    targets, attempts,
                    recoveryPolicy.attention(root.status(), root.runningSince(), root.completedAt(), attempts.size(), now),
                    action));
        } catch (OperationsReadException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new OperationsReadException();
        }
    }

    @Override
    public CacheInvalidationOperationsSummary summarize(Instant now) {
        Objects.requireNonNull(now, "now");
        try {
            return jdbc().queryForObject("""
                    WITH batches AS (
                        SELECT batch.status, batch.running_since, batch.completed_at,
                               (SELECT count(*) FROM operations.cache_purge_attempts attempt
                                WHERE attempt.batch_id = batch.id) AS attempt_count
                        FROM operations.cache_invalidation_batches batch
                    )
                    SELECT count(*) FILTER (WHERE status = 'REQUESTED') AS requested,
                           count(*) FILTER (WHERE status = 'RUNNING' AND running_since > :cutoff) AS running,
                           count(*) FILTER (WHERE status = 'RUNNING' AND running_since <= :cutoff) AS stranded,
                           count(*) FILTER (WHERE status IN ('FAILED', 'PARTIAL')
                               AND attempt_count < 3 AND completed_at IS NULL) AS retry_available,
                           count(*) FILTER (WHERE status IN ('FAILED', 'PARTIAL')
                               AND (attempt_count >= 3 OR completed_at IS NOT NULL)) AS retry_exhausted,
                           count(*) FILTER (WHERE status = 'COMPLETED') AS completed
                    FROM batches
                    """, Map.of("cutoff", Timestamp.from(recoveryPolicy.strandedCutoff(now))),
                    (rs, row) -> new CacheInvalidationOperationsSummary(
                            rs.getLong("requested"), rs.getLong("running"), rs.getLong("stranded"),
                            rs.getLong("retry_available"), rs.getLong("retry_exhausted"), rs.getLong("completed")));
        } catch (RuntimeException exception) {
            throw new OperationsReadException();
        }
    }

    private CacheInvalidationOperationsListItem mapListItem(ResultSet rs, Instant now) throws SQLException {
        CacheInvalidationStatus status = enumValue(CacheInvalidationStatus.class, rs.getString("status"));
        Instant runningSince = instant(rs, "running_since");
        Instant completedAt = instant(rs, "completed_at");
        int attempts = rs.getInt("attempt_count");
        return new CacheInvalidationOperationsListItem(
                CacheInvalidationBatchId.from(rs.getObject("id", UUID.class)),
                rs.getTimestamp("requested_at").toInstant(), status, runningSince, completedAt,
                rs.getInt("target_count"), attempts,
                nullableEnum(CachePurgeProvider.class, rs.getString("provider")),
                nullableEnum(CachePurgeResult.class, rs.getString("result")),
                nullableEnum(CachePurgeFailureReason.class, rs.getString("failure_reason")),
                recoveryPolicy.attention(status, runningSince, completedAt, attempts, now));
    }

    private static DetailRoot mapRoot(ResultSet rs) throws SQLException {
        return new DetailRoot(
                rs.getObject("id", UUID.class),
                enumValue(InvalidationReason.class, rs.getString("reason")),
                enumValue(InvalidationRequester.class, rs.getString("requested_by")),
                rs.getTimestamp("requested_at").toInstant(),
                enumValue(CacheInvalidationStatus.class, rs.getString("status")),
                instant(rs, "running_since"), instant(rs, "completed_at"),
                nullableEnum(CachePurgeFailureReason.class, rs.getString("failure_reason")),
                rs.getLong("version"));
    }

    private static CacheInvalidationOperationsTarget mapTarget(ResultSet rs) throws SQLException {
        CacheTargetType type = enumValue(CacheTargetType.class, rs.getString("target_type"));
        return new CacheInvalidationOperationsTarget(type,
                CacheTargetValue.of(type, rs.getString("target_value")),
                enumValue(CacheTargetStatus.class, rs.getString("status")));
    }

    private static CacheInvalidationOperationsAttempt mapAttempt(ResultSet rs) throws SQLException {
        return new CacheInvalidationOperationsAttempt(
                rs.getInt("attempt_number"),
                enumValue(CachePurgeProvider.class, rs.getString("provider")),
                rs.getTimestamp("attempted_at").toInstant(),
                enumValue(CachePurgeResult.class, rs.getString("result")),
                nullableEnum(CachePurgeFailureReason.class, rs.getString("failure_reason")));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
    private static <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        try { return Enum.valueOf(type, value); } catch (RuntimeException exception) { throw new OperationsReadException(); }
    }
    private static <E extends Enum<E>> E nullableEnum(Class<E> type, String value) {
        return value == null ? null : enumValue(type, value);
    }

    private NamedParameterJdbcTemplate jdbc() {
        NamedParameterJdbcTemplate available = jdbc.getIfAvailable();
        if (available == null) throw new OperationsReadException();
        return available;
    }

    private record DetailRoot(
            UUID id, InvalidationReason reason, InvalidationRequester requestedBy, Instant requestedAt,
            CacheInvalidationStatus status, Instant runningSince, Instant completedAt,
            CachePurgeFailureReason failureReason, long version) { }
}
