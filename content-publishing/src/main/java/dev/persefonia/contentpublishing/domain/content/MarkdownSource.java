package dev.persefonia.contentpublishing.domain.content;

import java.util.Objects;

public record MarkdownSource(String value) {
    public MarkdownSource {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new ContentValidationException("markdown source must not be blank");
        }
    }

    public static MarkdownSource of(String value) {
        return new MarkdownSource(value);
    }
}
