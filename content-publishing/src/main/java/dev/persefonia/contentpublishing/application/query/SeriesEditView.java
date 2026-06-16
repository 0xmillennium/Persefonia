package dev.persefonia.contentpublishing.application.query;

import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.model.series.SeriesId;
import dev.persefonia.contentpublishing.domain.model.series.SeriesStatus;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record SeriesEditView(
        SeriesId id,
        ContentLanguage language,
        String slug,
        String title,
        Optional<String> description,
        SeriesStatus status,
        List<SeriesEntryView> entries,
        List<SeriesCandidateContentItem> candidates) {
    public SeriesEditView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(title, "title");
        description = Objects.requireNonNull(description, "description");
        Objects.requireNonNull(status, "status");
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
    }
}
