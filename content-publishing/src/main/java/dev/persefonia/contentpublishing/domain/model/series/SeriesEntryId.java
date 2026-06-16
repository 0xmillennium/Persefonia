package dev.persefonia.contentpublishing.domain.model.series;

import java.util.Objects;
import java.util.UUID;

public record SeriesEntryId(UUID value) {
    public SeriesEntryId {
        Objects.requireNonNull(value, "value");
    }

    public static SeriesEntryId newId() {
        return new SeriesEntryId(UUID.randomUUID());
    }

    public static SeriesEntryId from(UUID value) {
        return new SeriesEntryId(value);
    }
}
