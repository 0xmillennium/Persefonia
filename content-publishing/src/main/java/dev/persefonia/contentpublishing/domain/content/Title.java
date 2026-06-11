package dev.persefonia.contentpublishing.domain.content;

import java.util.Objects;

public record Title(String value) {
    private static final int MAX_LENGTH = 200;

    public Title {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (value.isBlank()) {
            throw new ContentValidationException("title must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new ContentValidationException("title must not exceed " + MAX_LENGTH + " characters");
        }
    }

    public static Title of(String value) {
        return new Title(value);
    }
}
