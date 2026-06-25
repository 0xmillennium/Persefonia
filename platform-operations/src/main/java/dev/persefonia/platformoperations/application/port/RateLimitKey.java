package dev.persefonia.platformoperations.application.port;

import java.util.Objects;

public record RateLimitKey(String value) {
    public RateLimitKey {
        Objects.requireNonNull(value, "value must not be null");
        value = value.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        if (value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("value must not contain control characters");
        }
    }
}
