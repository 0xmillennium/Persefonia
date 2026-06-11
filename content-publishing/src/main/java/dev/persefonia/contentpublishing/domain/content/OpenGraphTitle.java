package dev.persefonia.contentpublishing.domain.content;

import java.util.Objects;

public record OpenGraphTitle(String value) {
    private static final int MAX_LENGTH = 200;

    public OpenGraphTitle {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (value.isBlank()) {
            throw new ContentValidationException("open graph title must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new ContentValidationException("open graph title must not exceed " + MAX_LENGTH + " characters");
        }
    }

    public static OpenGraphTitle of(String value) {
        return new OpenGraphTitle(value);
    }
}
