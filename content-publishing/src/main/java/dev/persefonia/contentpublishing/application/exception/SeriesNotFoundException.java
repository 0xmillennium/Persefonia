package dev.persefonia.contentpublishing.application.exception;

import dev.persefonia.contentpublishing.domain.model.series.SeriesId;

public final class SeriesNotFoundException extends ContentApplicationException {
    public SeriesNotFoundException(SeriesId seriesId) {
        super("Series not found: " + seriesId.value());
    }
}
