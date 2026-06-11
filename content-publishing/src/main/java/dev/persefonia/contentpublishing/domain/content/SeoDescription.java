package dev.persefonia.contentpublishing.domain.content;

import java.util.Objects;

public record SeoDescription(String value) {
    private static final int MAX_LENGTH = 500;

    public SeoDescription {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (value.isBlank()) {
            throw new ContentValidationException("seo description must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new ContentValidationException("seo description must not exceed " + MAX_LENGTH + " characters");
        }
    }

    public static SeoDescription of(String value) {
        return new SeoDescription(value);
    }
}
