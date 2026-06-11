package dev.persefonia.contentpublishing.domain.content;

import java.util.Objects;

public record RendererVersion(String value) {
    public RendererVersion {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (value.isBlank()) {
            throw new ContentValidationException("renderer version must not be blank");
        }
    }

    public static RendererVersion of(String value) {
        return new RendererVersion(value);
    }
}
