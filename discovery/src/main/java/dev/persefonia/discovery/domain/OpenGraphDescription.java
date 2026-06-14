package dev.persefonia.discovery.domain;

import java.util.Objects;

public record OpenGraphDescription(String value) {
    private static final int MAX_LENGTH = 500;

    public OpenGraphDescription {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("open graph description must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("open graph description must not exceed " + MAX_LENGTH + " characters");
        }
    }
}
