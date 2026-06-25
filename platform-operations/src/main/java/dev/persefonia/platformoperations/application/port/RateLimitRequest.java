package dev.persefonia.platformoperations.application.port;

import java.time.Duration;
import java.util.Objects;

public record RateLimitRequest(
        RateLimitScope scope,
        RateLimitKey key,
        int maxAttempts,
        Duration window) {
    public RateLimitRequest {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(window, "window must not be null");
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        if (window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("window must be positive");
        }
    }
}
