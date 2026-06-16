package dev.persefonia.contentpublishing.application.command;

import dev.persefonia.contentpublishing.application.authorization.ContentCommandActor;
import dev.persefonia.contentpublishing.domain.model.series.SeriesEntryId;
import dev.persefonia.contentpublishing.domain.model.series.SeriesId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record ReorderSeriesEntriesCommand(
        ContentCommandActor actor,
        SeriesId seriesId,
        List<SeriesEntryId> orderedEntryIds,
        Instant requestedAt) {
    public ReorderSeriesEntriesCommand {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(seriesId, "seriesId");
        orderedEntryIds = List.copyOf(Objects.requireNonNull(orderedEntryIds, "orderedEntryIds"));
        Objects.requireNonNull(requestedAt, "requestedAt");
    }
}
