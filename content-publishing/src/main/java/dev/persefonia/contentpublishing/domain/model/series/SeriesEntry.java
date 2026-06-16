package dev.persefonia.contentpublishing.domain.model.series;

import dev.persefonia.contentpublishing.domain.content.ContentId;
import java.time.Instant;
import java.util.Objects;

public record SeriesEntry(
        SeriesEntryId id,
        ContentId contentItemId,
        SeriesEntryPosition position,
        Instant addedAt) {
    public SeriesEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(contentItemId, "contentItemId");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(addedAt, "addedAt");
    }

    SeriesEntry withPosition(SeriesEntryPosition newPosition) {
        return new SeriesEntry(id, contentItemId, newPosition, addedAt);
    }
}
