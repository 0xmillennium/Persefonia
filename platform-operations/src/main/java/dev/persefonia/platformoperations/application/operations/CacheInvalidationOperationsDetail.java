package dev.persefonia.platformoperations.application.operations;

import dev.persefonia.platformoperations.domain.cache.*;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record CacheInvalidationOperationsDetail(
        CacheInvalidationBatchId id,
        InvalidationReason reason,
        InvalidationRequester requestedBy,
        Instant requestedAt,
        CacheInvalidationStatus status,
        Instant runningSince,
        Instant completedAt,
        CachePurgeFailureReason failureReason,
        long version,
        List<CacheInvalidationOperationsTarget> targets,
        List<CacheInvalidationOperationsAttempt> attempts,
        CacheInvalidationAttentionState attentionState,
        CacheRecoveryAction availableAction) {
    public CacheInvalidationOperationsDetail {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(requestedBy, "requestedBy"); Objects.requireNonNull(requestedAt, "requestedAt");
        Objects.requireNonNull(status, "status"); Objects.requireNonNull(attentionState, "attentionState");
        Objects.requireNonNull(availableAction, "availableAction");
        targets = List.copyOf(Objects.requireNonNull(targets, "targets"));
        attempts = List.copyOf(Objects.requireNonNull(attempts, "attempts"));
        if (version < 0 || targets.isEmpty()) throw new IllegalArgumentException("invalid operation detail");
    }
    public Optional<Instant> runningSinceOptional() { return Optional.ofNullable(runningSince); }
    public Optional<Instant> completedAtOptional() { return Optional.ofNullable(completedAt); }
    public Optional<CachePurgeFailureReason> failureReasonOptional() { return Optional.ofNullable(failureReason); }
}
