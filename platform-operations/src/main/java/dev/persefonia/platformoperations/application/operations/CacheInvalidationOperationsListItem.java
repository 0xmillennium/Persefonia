package dev.persefonia.platformoperations.application.operations;

import dev.persefonia.platformoperations.domain.cache.*;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record CacheInvalidationOperationsListItem(
        CacheInvalidationBatchId id,
        Instant requestedAt,
        CacheInvalidationStatus status,
        Instant runningSince,
        Instant completedAt,
        int targetCount,
        int attemptCount,
        CachePurgeProvider latestProvider,
        CachePurgeResult latestResult,
        CachePurgeFailureReason latestFailureReason,
        CacheInvalidationAttentionState attentionState) {
    public CacheInvalidationOperationsListItem {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(requestedAt, "requestedAt");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(attentionState, "attentionState");
        if (targetCount < 1 || attemptCount < 0 || attemptCount > 3) {
            throw new IllegalArgumentException("invalid cache invalidation operation counts");
        }
    }
    public Optional<Instant> runningSinceOptional() { return Optional.ofNullable(runningSince); }
    public Optional<Instant> completedAtOptional() { return Optional.ofNullable(completedAt); }
    public Optional<CachePurgeProvider> latestProviderOptional() { return Optional.ofNullable(latestProvider); }
    public Optional<CachePurgeResult> latestResultOptional() { return Optional.ofNullable(latestResult); }
    public Optional<CachePurgeFailureReason> latestFailureReasonOptional() { return Optional.ofNullable(latestFailureReason); }
}
