package dev.persefonia.contentpublishing.application.command;

import dev.persefonia.contentpublishing.application.authorization.ContentCommandActor;
import dev.persefonia.contentpublishing.domain.model.series.SeriesId;
import java.time.Instant;
import java.util.Objects;

public record UpdateSeriesCommand(
        ContentCommandActor actor,
        SeriesId seriesId,
        String title,
        String slug,
        String description,
        Instant requestedAt) {
    public UpdateSeriesCommand {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(seriesId, "seriesId");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(requestedAt, "requestedAt");
    }
}
