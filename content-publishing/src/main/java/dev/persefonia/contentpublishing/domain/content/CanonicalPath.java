package dev.persefonia.contentpublishing.domain.content;

import java.util.Objects;

public record CanonicalPath(String value) {
    private static final int MAX_LENGTH = 512;

    public CanonicalPath {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new ContentValidationException("canonical path must not be blank");
        }
        if (!value.startsWith("/")) {
            throw new ContentValidationException("canonical path must start with /");
        }
        if (value.chars().anyMatch(Character::isWhitespace)) {
            throw new ContentValidationException("canonical path must not contain whitespace");
        }
        if (value.length() > MAX_LENGTH) {
            throw new ContentValidationException("canonical path must not exceed " + MAX_LENGTH + " characters");
        }
    }

    public static CanonicalPath of(String value) {
        return new CanonicalPath(value);
    }
}
