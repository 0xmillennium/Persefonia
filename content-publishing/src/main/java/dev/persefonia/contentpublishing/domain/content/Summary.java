package dev.persefonia.contentpublishing.domain.content;

import java.util.Objects;

public record Summary(String value) {
    private static final int MAX_LENGTH = 500;

    public Summary {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (value.isBlank()) {
            throw new ContentValidationException("summary must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new ContentValidationException("summary must not exceed " + MAX_LENGTH + " characters");
        }
    }

    public static Summary of(String value) {
        return new Summary(value);
    }
}
