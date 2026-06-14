package dev.persefonia.discovery.domain;

import java.util.Objects;

public record ResourceTitle(String value) {
    private static final int MAX_LENGTH = 200;

    public ResourceTitle {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("resource title must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("resource title must not exceed " + MAX_LENGTH + " characters");
        }
    }
}
