package dev.persefonia.discovery.domain;

import java.util.Objects;

public record SearchText(String value) {
    public SearchText {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("search text must not be blank");
        }
    }
}
