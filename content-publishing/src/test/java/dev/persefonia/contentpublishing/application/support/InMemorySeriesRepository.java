package dev.persefonia.contentpublishing.application.support;

import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.model.series.Series;
import dev.persefonia.contentpublishing.domain.model.series.SeriesId;
import dev.persefonia.contentpublishing.domain.model.series.SeriesSlug;
import dev.persefonia.contentpublishing.domain.model.series.SeriesSummary;
import dev.persefonia.contentpublishing.domain.model.series.port.SeriesRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class InMemorySeriesRepository implements SeriesRepository {
    private final Map<SeriesId, Series> series = new LinkedHashMap<>();

    @Override
    public List<SeriesSummary> findActiveContaining(
            dev.persefonia.contentpublishing.domain.content.ContentId contentId, ContentLanguage language) {
        return findAllForAdmin().stream()
                .filter(value -> value.language() == language
                        && value.status() == dev.persefonia.contentpublishing.domain.model.series.SeriesStatus.ACTIVE
                        && series.get(value.id()).containsContentItem(contentId))
                .sorted(java.util.Comparator.comparing((SeriesSummary value) -> value.title().value())
                        .thenComparing(value -> value.id().value()))
                .toList();
    }

    @Override
    public Series save(Series value) {
        series.put(value.id(), value);
        return value;
    }

    @Override
    public Optional<Series> findById(SeriesId id) {
        return Optional.ofNullable(series.get(id));
    }

    @Override
    public Optional<Series> findByLanguageAndSlug(ContentLanguage language, SeriesSlug slug) {
        return series.values().stream()
                .filter(value -> value.language() == language && value.slug().equals(slug))
                .findFirst();
    }

    @Override
    public boolean existsByLanguageAndSlug(ContentLanguage language, SeriesSlug slug) {
        return findByLanguageAndSlug(language, slug).isPresent();
    }

    @Override
    public List<SeriesSummary> findAllForAdmin() {
        return series.values().stream()
                .map(value -> new SeriesSummary(
                        value.id(),
                        value.language(),
                        value.slug(),
                        value.title(),
                        value.status(),
                        value.entries().size(),
                        value.updatedAt()))
                .toList();
    }
}
