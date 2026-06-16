package dev.persefonia.contentpublishing.application.query;

import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.model.series.SeriesId;
import dev.persefonia.contentpublishing.domain.model.series.SeriesStatus;
import java.time.Instant;
import java.util.Objects;

public record SeriesListItem(
        SeriesId id,
        ContentLanguage language,
        String slug,
        String title,
        SeriesStatus status,
        int entryCount,
        Instant updatedAt) {
    public SeriesListItem {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
