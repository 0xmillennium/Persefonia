package dev.persefonia.contentpublishing.application.service;

import dev.persefonia.contentpublishing.application.port.PublicSeriesEntryReadModel;
import dev.persefonia.contentpublishing.application.query.PublicSeriesBySourceQuery;
import dev.persefonia.contentpublishing.application.query.PublicSeriesLookupResult;
import dev.persefonia.contentpublishing.application.query.PublicSeriesPageResult;
import dev.persefonia.contentpublishing.domain.model.series.Series;
import dev.persefonia.contentpublishing.domain.model.series.SeriesDescription;
import dev.persefonia.contentpublishing.domain.model.series.SeriesId;
import dev.persefonia.contentpublishing.domain.model.series.SeriesStatus;
import dev.persefonia.contentpublishing.domain.model.series.port.SeriesRepository;
import java.util.Objects;

public final class PublicSeriesPageQueryService {
    private final SeriesRepository seriesRepository;
    private final PublicSeriesEntryReadModel entries;

    public PublicSeriesPageQueryService(
            SeriesRepository seriesRepository,
            PublicSeriesEntryReadModel entries) {
        this.seriesRepository = Objects.requireNonNull(seriesRepository, "seriesRepository");
        this.entries = Objects.requireNonNull(entries, "entries");
    }

    public PublicSeriesLookupResult lookup(PublicSeriesBySourceQuery query) {
        Objects.requireNonNull(query, "query");
        return seriesRepository.findById(SeriesId.from(query.seriesId()))
                .filter(series -> series.status() == SeriesStatus.ACTIVE)
                .filter(series -> series.language() == query.language())
                .filter(series -> series.slug().value().equals(query.expectedSlug()))
                .<PublicSeriesLookupResult>map(this::found)
                .orElseGet(PublicSeriesLookupResult.NotFound::new);
    }

    private PublicSeriesLookupResult found(Series series) {
        return new PublicSeriesLookupResult.Found(new PublicSeriesPageResult(
                series.id().value(),
                series.language(),
                series.title().value(),
                series.slug().value(),
                series.description().map(SeriesDescription::value).orElse(""),
                series.status(),
                entries.listEntries(series.id(), series.language())));
    }
}
