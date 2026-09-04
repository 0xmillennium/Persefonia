package dev.persefonia.platformoperations.application.operations;

import dev.persefonia.platformoperations.domain.cache.*;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record CacheInvalidationOperationsAttempt(
        int attemptNumber,
        CachePurgeProvider provider,
        Instant attemptedAt,
        CachePurgeResult result,
        CachePurgeFailureReason failureReason) {
    public CacheInvalidationOperationsAttempt {
        if (attemptNumber < 1 || attemptNumber > 3) throw new IllegalArgumentException("invalid attempt number");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(attemptedAt, "attemptedAt");
        Objects.requireNonNull(result, "result");
    }
    public Optional<CachePurgeFailureReason> failureReasonOptional() { return Optional.ofNullable(failureReason); }
}
