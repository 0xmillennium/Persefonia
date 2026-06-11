package dev.persefonia.contentpublishing.domain.content;

import java.util.Objects;

public record RenderedHtml(String value) {
    public RenderedHtml {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new ContentValidationException("rendered html must not be blank");
        }
    }

    public static RenderedHtml sanitized(String html) {
        return new RenderedHtml(html);
    }
}
