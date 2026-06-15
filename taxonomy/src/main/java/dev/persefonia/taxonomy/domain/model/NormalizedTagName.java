package dev.persefonia.taxonomy.domain.model;

import java.util.Objects;

public record NormalizedTagName(String value) {
    public NormalizedTagName {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new TagValidationException("normalized tag name must not be blank");
        }
        if (value.length() > TagName.MAX_LENGTH) {
            throw new TagValidationException(
                    "normalized tag name must not exceed " + TagName.MAX_LENGTH + " characters");
        }
    }

    public static NormalizedTagName ofCanonical(String value) {
        return new NormalizedTagName(value);
    }
}
