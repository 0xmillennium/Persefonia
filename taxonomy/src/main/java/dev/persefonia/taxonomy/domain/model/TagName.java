package dev.persefonia.taxonomy.domain.model;

import java.util.Objects;

public record TagName(String value) {
    public static final int MAX_LENGTH = 80;

    public TagName {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (value.isBlank()) {
            throw new TagValidationException("tag name must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new TagValidationException("tag name must not exceed " + MAX_LENGTH + " characters");
        }
    }

    public static TagName of(String value) {
        return new TagName(value);
    }
}
