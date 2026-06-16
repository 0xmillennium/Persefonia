package dev.persefonia.contentpublishing.domain.model.series;

import java.util.Objects;
import java.util.UUID;

public record SeriesId(UUID value) {
    public SeriesId {
        Objects.requireNonNull(value, "value");
    }

    public static SeriesId newId() {
        return new SeriesId(UUID.randomUUID());
    }

    public static SeriesId from(UUID value) {
        return new SeriesId(value);
    }
}
