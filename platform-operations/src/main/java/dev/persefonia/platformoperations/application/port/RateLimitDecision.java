package dev.persefonia.platformoperations.application.port;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

public record RateLimitDecision(
        boolean allowed,
        RateLimitRejectionReason rejectionReason,
        Duration retryAfter,
        long remainingAttempts) {
    public RateLimitDecision {
        if (allowed && rejectionReason != null) {
            throw new IllegalArgumentException("allowed rate-limit decision cannot include a rejection reason");
        }
        if (!allowed) {
            Objects.requireNonNull(rejectionReason, "rejectionReason must not be null for rejected rate-limit decision");
        }
        if (retryAfter != null && retryAfter.isNegative()) {
            throw new IllegalArgumentException("retryAfter must not be negative");
        }
        if (remainingAttempts < 0) {
            throw new IllegalArgumentException("remainingAttempts must not be negative");
        }
    }

    public static RateLimitDecision allowed(long remainingAttempts) {
        return new RateLimitDecision(true, null, null, remainingAttempts);
    }

    public static RateLimitDecision rejected(RateLimitRejectionReason reason, Duration retryAfter) {
        return new RateLimitDecision(false, reason, retryAfter, 0);
    }

    public Optional<RateLimitRejectionReason> rejectionReasonValue() {
        return Optional.ofNullable(rejectionReason);
    }

    public Optional<Duration> retryAfterValue() {
        return Optional.ofNullable(retryAfter);
    }
}
