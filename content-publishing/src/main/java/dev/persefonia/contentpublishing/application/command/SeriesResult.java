package dev.persefonia.contentpublishing.application.command;

import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.model.series.SeriesId;
import java.util.Objects;

public record SeriesResult(SeriesId seriesId, ContentId contentItemId, boolean mutated) {
    public SeriesResult(SeriesId seriesId) {
        this(seriesId, null, true);
    }

    public SeriesResult(SeriesId seriesId, ContentId contentItemId) {
        this(seriesId, contentItemId, true);
    }

    public SeriesResult {
        Objects.requireNonNull(seriesId, "seriesId");
    }
}
