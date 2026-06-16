package dev.persefonia.contentpublishing.application.command;

import dev.persefonia.contentpublishing.application.authorization.ContentCommandActor;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.model.series.SeriesId;
import java.time.Instant;
import java.util.Objects;

public record AddSeriesEntryCommand(
        ContentCommandActor actor,
        SeriesId seriesId,
        ContentId contentItemId,
        Instant requestedAt) {
    public AddSeriesEntryCommand {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(seriesId, "seriesId");
        Objects.requireNonNull(contentItemId, "contentItemId");
        Objects.requireNonNull(requestedAt, "requestedAt");
    }
}
