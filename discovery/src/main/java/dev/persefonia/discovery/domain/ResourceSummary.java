package dev.persefonia.discovery.domain;

import java.util.Objects;

public record ResourceSummary(String value) {
    private static final int MAX_LENGTH = 500;

    public ResourceSummary {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("resource summary must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("resource summary must not exceed " + MAX_LENGTH + " characters");
        }
    }
}
