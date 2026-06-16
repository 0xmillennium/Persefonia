package dev.persefonia.contentpublishing.application.query;

import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.model.series.SeriesStatus;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record PublicSeriesPageResult(
        UUID seriesId,
        ContentLanguage language,
        String title,
        String slug,
        String description,
        SeriesStatus status,
        List<PublicSeriesEntryItem> entries) {
    public PublicSeriesPageResult {
        Objects.requireNonNull(seriesId, "seriesId");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(status, "status");
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
    }
}
