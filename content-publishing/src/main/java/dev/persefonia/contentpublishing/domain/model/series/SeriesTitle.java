package dev.persefonia.contentpublishing.domain.model.series;

import java.util.Objects;

public record SeriesTitle(String value) {
    private static final int MAX_LENGTH = 120;

    public SeriesTitle {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (value.isBlank()) {
            throw new SeriesValidationException("series title must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new SeriesValidationException("series title must not exceed " + MAX_LENGTH + " characters");
        }
    }

    public static SeriesTitle of(String value) {
        return new SeriesTitle(value);
    }
}
