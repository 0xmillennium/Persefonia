package dev.persefonia.discovery.application.contract;

import java.net.URI;
import java.util.Objects;

public record CanonicalUrl(String value) {
    public CanonicalUrl {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        if (value.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("value must not contain whitespace");
        }

        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("value must be a valid absolute URI", exception);
        }
        if (!uri.isAbsolute()) {
            throw new IllegalArgumentException("value must be an absolute URI");
        }
    }
}
