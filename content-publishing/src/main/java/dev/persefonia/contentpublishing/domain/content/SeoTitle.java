package dev.persefonia.contentpublishing.domain.content;

import java.util.Objects;

public record SeoTitle(String value) {
    private static final int MAX_LENGTH = 200;

    public SeoTitle {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (value.isBlank()) {
            throw new ContentValidationException("seo title must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new ContentValidationException("seo title must not exceed " + MAX_LENGTH + " characters");
        }
    }

    public static SeoTitle of(String value) {
        return new SeoTitle(value);
    }
}
