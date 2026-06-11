package dev.persefonia.contentpublishing.domain.content;

import java.util.Objects;

public record HeadingText(String value) {
    public HeadingText {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (value.isBlank()) {
            throw new ContentValidationException("heading text must not be blank");
        }
    }

    public static HeadingText of(String value) {
        return new HeadingText(value);
    }
}
