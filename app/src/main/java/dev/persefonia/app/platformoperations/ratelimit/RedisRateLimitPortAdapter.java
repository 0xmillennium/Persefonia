package dev.persefonia.app.platformoperations.ratelimit;

import dev.persefonia.platformoperations.application.port.RateLimitDecision;
import dev.persefonia.platformoperations.application.port.RateLimitPort;
import dev.persefonia.platformoperations.application.port.RateLimitRejectionReason;
import dev.persefonia.platformoperations.application.port.RateLimitRequest;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

public final class RedisRateLimitPortAdapter implements RateLimitPort {
    private final RedisCounterStore counters;
    private final String keyPrefix;

    public RedisRateLimitPortAdapter(RedisCounterStore counters, String keyPrefix) {
        this.counters = Objects.requireNonNull(counters, "counters must not be null");
        if (keyPrefix == null || keyPrefix.isBlank()) {
            throw new IllegalArgumentException("keyPrefix must not be blank");
        }
        this.keyPrefix = keyPrefix.trim();
    }

    @Override
    public RateLimitDecision checkAndConsume(RateLimitRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        try {
            String key = redisKey(request);
            long used = counters.increment(key);
            if (used == 1L) {
                counters.expire(key, request.window());
            }
            if (used > request.maxAttempts()) {
                return RateLimitDecision.rejected(RateLimitRejectionReason.LIMIT_EXCEEDED, request.window());
            }
            return RateLimitDecision.allowed(request.maxAttempts() - used);
        } catch (RuntimeException exception) {
            return RateLimitDecision.rejected(RateLimitRejectionReason.TEMPORARILY_UNAVAILABLE, Duration.ZERO);
        }
    }

    private String redisKey(RateLimitRequest request) {
        return keyPrefix
                + ":"
                + request.scope().name().toLowerCase(Locale.ROOT)
                + ":"
                + request.key().value();
    }
}
