package dev.persefonia.contentpublishing.application.command;

import dev.persefonia.contentpublishing.application.authorization.ContentCommandActor;
import dev.persefonia.contentpublishing.domain.model.series.SeriesEntryId;
import dev.persefonia.contentpublishing.domain.model.series.SeriesId;
import java.time.Instant;
import java.util.Objects;

public record RemoveSeriesEntryCommand(
        ContentCommandActor actor,
        SeriesId seriesId,
        SeriesEntryId entryId,
        Instant requestedAt) {
    public RemoveSeriesEntryCommand {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(seriesId, "seriesId");
        Objects.requireNonNull(entryId, "entryId");
        Objects.requireNonNull(requestedAt, "requestedAt");
    }
}
