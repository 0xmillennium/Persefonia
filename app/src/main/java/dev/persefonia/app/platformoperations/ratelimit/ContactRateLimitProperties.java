package dev.persefonia.app.platformoperations.ratelimit;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "persefonia.contact.rate-limit")
public record ContactRateLimitProperties(
        String secret,
        int maxAttempts,
        Duration window,
        String redisKeyPrefix) {
    public ContactRateLimitProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("contact rate-limit secret must not be blank");
        }
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("contact rate-limit max attempts must be positive");
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("contact rate-limit window must be positive");
        }
        if (redisKeyPrefix == null || redisKeyPrefix.isBlank()) {
            throw new IllegalArgumentException("contact rate-limit Redis key prefix must not be blank");
        }
        redisKeyPrefix = redisKeyPrefix.trim();
    }
}
