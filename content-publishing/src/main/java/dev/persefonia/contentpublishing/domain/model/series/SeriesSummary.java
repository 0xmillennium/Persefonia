package dev.persefonia.contentpublishing.domain.model.series;

import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import java.time.Instant;
import java.util.Objects;

public record SeriesSummary(
        SeriesId id,
        ContentLanguage language,
        SeriesSlug slug,
        SeriesTitle title,
        SeriesStatus status,
        int entryCount,
        Instant updatedAt) {
    public SeriesSummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (entryCount < 0) {
            throw new IllegalArgumentException("entryCount must not be negative");
        }
    }
}
