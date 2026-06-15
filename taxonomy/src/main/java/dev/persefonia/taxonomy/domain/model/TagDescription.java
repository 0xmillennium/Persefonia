package dev.persefonia.taxonomy.domain.model;

import java.util.Optional;

public record TagDescription(Optional<String> value) {
    public static final int MAX_LENGTH = 500;

    public TagDescription {
        value = value == null ? Optional.empty() : value.map(String::trim).filter(text -> !text.isBlank());
        if (value.map(String::length).orElse(0) > MAX_LENGTH) {
            throw new TagValidationException("tag description must not exceed " + MAX_LENGTH + " characters");
        }
    }

    public static TagDescription ofNullable(String value) {
        return new TagDescription(Optional.ofNullable(value));
    }

    public static TagDescription empty() {
        return new TagDescription(Optional.empty());
    }
}
