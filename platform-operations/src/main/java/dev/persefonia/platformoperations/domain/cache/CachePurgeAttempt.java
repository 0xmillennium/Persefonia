package dev.persefonia.platformoperations.domain.cache;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record CachePurgeAttempt(
        CachePurgeAttemptId id,
        int attemptNumber,
        CachePurgeProvider provider,
        Instant attemptedAt,
        CachePurgeResult result,
        CachePurgeFailureReason failureReason) {
    public CachePurgeAttempt {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(attemptedAt, "attemptedAt");
        Objects.requireNonNull(result, "result");
        if (attemptNumber < 1 || attemptNumber > 3) {
            throw new CacheInvalidationValidationException("attempt number must be between 1 and 3");
        }
        if (result == CachePurgeResult.SUCCESS && failureReason != null) {
            throw new CacheInvalidationValidationException("successful attempt must not have a failure reason");
        }
        if (result == CachePurgeResult.FAILED && failureReason == null) {
            throw new CacheInvalidationValidationException("failed attempt requires a failure reason");
        }
    }

    public static CachePurgeAttempt rehydrate(CachePurgeAttemptId id, int attemptNumber,
            CachePurgeProvider provider, Instant attemptedAt, CachePurgeResult result,
            CachePurgeFailureReason failureReason) {
        return new CachePurgeAttempt(id, attemptNumber, provider, attemptedAt, result, failureReason);
    }

    public Optional<CachePurgeFailureReason> failureReasonOptional() { return Optional.ofNullable(failureReason); }
}
