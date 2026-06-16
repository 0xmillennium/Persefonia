package dev.persefonia.contentpublishing.domain.model.series;

import java.util.Objects;
import java.util.Optional;

public record SeriesDescription(String value) {
    private static final int MAX_LENGTH = 500;

    public SeriesDescription {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (value.isBlank()) {
            throw new SeriesValidationException("series description must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new SeriesValidationException("series description must not exceed " + MAX_LENGTH + " characters");
        }
    }

    public static Optional<SeriesDescription> optional(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new SeriesDescription(value));
    }
}
