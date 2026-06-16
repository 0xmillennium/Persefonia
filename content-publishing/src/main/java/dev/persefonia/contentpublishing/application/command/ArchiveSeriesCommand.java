package dev.persefonia.contentpublishing.application.command;

import dev.persefonia.contentpublishing.application.authorization.ContentCommandActor;
import dev.persefonia.contentpublishing.domain.model.series.SeriesId;
import java.time.Instant;
import java.util.Objects;

public record ArchiveSeriesCommand(
        ContentCommandActor actor,
        SeriesId seriesId,
        Instant requestedAt) {
    public ArchiveSeriesCommand {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(seriesId, "seriesId");
        Objects.requireNonNull(requestedAt, "requestedAt");
    }
}
