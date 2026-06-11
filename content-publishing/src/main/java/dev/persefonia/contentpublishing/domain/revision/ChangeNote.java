package dev.persefonia.contentpublishing.domain.revision;

import dev.persefonia.contentpublishing.domain.content.ContentValidationException;
import java.util.Objects;

public record ChangeNote(String value) {
    private static final int MAX_LENGTH = 1000;

    public ChangeNote {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (value.isBlank()) {
            throw new ContentValidationException("change note must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new ContentValidationException("change note must not exceed " + MAX_LENGTH + " characters");
        }
    }

    public static ChangeNote of(String value) {
        return new ChangeNote(value);
    }
}
