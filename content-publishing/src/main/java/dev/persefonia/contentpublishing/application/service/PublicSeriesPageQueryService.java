package dev.persefonia.contentpublishing.application.service;

import dev.persefonia.contentpublishing.application.discovery.ContentPublicRouteFactory;
import dev.persefonia.contentpublishing.application.query.PublicSeriesBySourceQuery;
import dev.persefonia.contentpublishing.application.query.PublicSeriesEntryItem;
import dev.persefonia.contentpublishing.application.query.PublicSeriesLookupResult;
import dev.persefonia.contentpublishing.application.query.PublicSeriesPageResult;
import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.content.port.ContentItemRepository;
import dev.persefonia.contentpublishing.domain.model.series.Series;
import dev.persefonia.contentpublishing.domain.model.series.SeriesDescription;
import dev.persefonia.contentpublishing.domain.model.series.SeriesEntry;
import dev.persefonia.contentpublishing.domain.model.series.SeriesId;
import dev.persefonia.contentpublishing.domain.model.series.SeriesStatus;
import dev.persefonia.contentpublishing.domain.model.series.port.SeriesRepository;
import java.util.Objects;
import java.util.Optional;

public final class PublicSeriesPageQueryService {
    private final ContentItemRepository items;
    private final SeriesRepository seriesRepository;
    private final ContentPublicRouteFactory routeFactory;

    public PublicSeriesPageQueryService(
            ContentItemRepository items,
            SeriesRepository seriesRepository,
            ContentPublicRouteFactory routeFactory) {
        this.items = Objects.requireNonNull(items, "items");
        this.seriesRepository = Objects.requireNonNull(seriesRepository, "seriesRepository");
        this.routeFactory = Objects.requireNonNull(routeFactory, "routeFactory");
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
                series.entries().stream()
                        .map(entry -> entryItem(series, entry))
                        .flatMap(Optional::stream)
                        .toList()));
    }

    private Optional<PublicSeriesEntryItem> entryItem(Series series, SeriesEntry entry) {
        return items.findById(entry.contentItemId())
                .filter(ContentItem::isListedPublicly)
                .filter(item -> item.language() == series.language())
                .filter(item -> item.renderSnapshot().isPresent())
                .filter(this::currentPublicPathIsValid)
                .map(item -> toResult(entry, item));
    }

    private boolean currentPublicPathIsValid(ContentItem item) {
        return item.slug()
                .map(slug -> routeFactory.publicUrl(item.type(), item.language(), slug).value())
                .filter(path -> item.metadata().canonicalPath().map(canonical -> canonical.value().equals(path)).orElse(false))
                .isPresent();
    }

    private PublicSeriesEntryItem toResult(SeriesEntry entry, ContentItem item) {
        String publicUrl = routeFactory.publicUrl(item.type(), item.language(), item.slug().orElseThrow()).value();
        return new PublicSeriesEntryItem(
                entry.position().value(),
                item.title().orElseThrow().value(),
                item.summary().orElseThrow().value(),
                publicUrl,
                item.metadata().canonicalPath().orElseThrow().value(),
                item.type().name(),
                item.publishedAt().orElseThrow(),
                item.language().name());
    }
}
