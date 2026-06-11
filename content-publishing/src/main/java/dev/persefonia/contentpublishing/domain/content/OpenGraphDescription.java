package dev.persefonia.contentpublishing.domain.content;

import java.util.Objects;

public record OpenGraphDescription(String value) {
    private static final int MAX_LENGTH = 500;

    public OpenGraphDescription {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (value.isBlank()) {
            throw new ContentValidationException("open graph description must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new ContentValidationException("open graph description must not exceed " + MAX_LENGTH + " characters");
        }
    }

    public static OpenGraphDescription of(String value) {
        return new OpenGraphDescription(value);
    }
}
