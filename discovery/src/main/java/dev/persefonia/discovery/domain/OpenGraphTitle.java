package dev.persefonia.discovery.domain;

import java.util.Objects;

public record OpenGraphTitle(String value) {
    private static final int MAX_LENGTH = 200;

    public OpenGraphTitle {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("open graph title must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("open graph title must not exceed " + MAX_LENGTH + " characters");
        }
    }
}
