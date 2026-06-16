package dev.persefonia.contentpublishing.application.command;

import dev.persefonia.contentpublishing.domain.model.series.SeriesId;
import java.util.Objects;

public record SeriesResult(SeriesId seriesId) {
    public SeriesResult {
        Objects.requireNonNull(seriesId, "seriesId");
    }
}
