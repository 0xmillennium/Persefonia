package dev.persefonia.platformoperations.application.operations;

import dev.persefonia.platformoperations.domain.cache.CacheInvalidationBatch;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class CacheInvalidationRecoveryPolicy {
    private static final int MAX_ATTEMPTS = 3;
    private final Duration strandedAfter;

    public CacheInvalidationRecoveryPolicy(Duration strandedAfter) {
        this.strandedAfter = Objects.requireNonNull(strandedAfter, "strandedAfter");
        if (strandedAfter.compareTo(Duration.ofMinutes(1)) < 0
                || strandedAfter.compareTo(Duration.ofHours(24)) > 0) {
            throw new IllegalArgumentException("stranded threshold must be between 1 minute and 24 hours");
        }
    }

    public CacheRecoveryAction availableAction(CacheInvalidationBatch batch, Instant now) {
        Objects.requireNonNull(batch, "batch");
        Objects.requireNonNull(now, "now");
        return action(batch.status(), batch.runningSince().orElse(null), batch.completedAt().orElse(null),
                batch.attempts().size(), now);
    }

    public CacheRecoveryAction action(
            CacheInvalidationStatus status,
            Instant runningSince,
            Instant completedAt,
            int attemptCount,
            Instant now) {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(now, "now");
        if (attemptCount < 0 || attemptCount > MAX_ATTEMPTS) {
            throw new IllegalArgumentException("attempt count is outside the cache purge budget");
        }
        return switch (status) {
            case REQUESTED -> CacheRecoveryAction.EXECUTE_INITIAL;
            case RUNNING -> runningSince != null && !runningSince.isAfter(strandedCutoff(now))
                    ? CacheRecoveryAction.RESUME_STRANDED : CacheRecoveryAction.NONE;
            case FAILED, PARTIAL -> attemptCount < MAX_ATTEMPTS && completedAt == null
                    ? CacheRecoveryAction.RETRY_FAILED : CacheRecoveryAction.NONE;
            case COMPLETED -> CacheRecoveryAction.NONE;
        };
    }

    public CacheInvalidationAttentionState attention(
            CacheInvalidationStatus status,
            Instant runningSince,
            Instant completedAt,
            int attemptCount,
            Instant now) {
        CacheRecoveryAction action = action(status, runningSince, completedAt, attemptCount, now);
        return switch (status) {
            case REQUESTED -> CacheInvalidationAttentionState.PENDING_INITIAL;
            case RUNNING -> action == CacheRecoveryAction.RESUME_STRANDED
                    ? CacheInvalidationAttentionState.STRANDED : CacheInvalidationAttentionState.RUNNING;
            case FAILED, PARTIAL -> action == CacheRecoveryAction.RETRY_FAILED
                    ? CacheInvalidationAttentionState.RETRY_AVAILABLE
                    : CacheInvalidationAttentionState.RETRY_EXHAUSTED;
            case COMPLETED -> CacheInvalidationAttentionState.COMPLETED;
        };
    }

    public Instant strandedCutoff(Instant now) {
        return Objects.requireNonNull(now, "now").minus(strandedAfter);
    }

    public Duration strandedAfter() {
        return strandedAfter;
    }
}
