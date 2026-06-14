package dev.persefonia.discovery.application.contract;

import java.util.Objects;

public record PublicUrl(String value) {
    public PublicUrl {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        if (!value.startsWith("/") || value.startsWith("//")) {
            throw new IllegalArgumentException("value must be a path starting with /");
        }
        if (value.contains("?")) {
            throw new IllegalArgumentException("value must not contain a query string");
        }
        if (value.contains("#")) {
            throw new IllegalArgumentException("value must not contain a fragment");
        }
        if (value.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("value must not contain whitespace");
        }
    }
}
