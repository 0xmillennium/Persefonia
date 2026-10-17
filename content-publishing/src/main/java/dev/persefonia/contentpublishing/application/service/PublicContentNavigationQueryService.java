package dev.persefonia.contentpublishing.application.service;

import dev.persefonia.contentpublishing.application.discovery.ContentPublicRouteFactory;
import dev.persefonia.contentpublishing.application.discovery.SeriesPublicRouteFactory;
import dev.persefonia.contentpublishing.application.port.ContentTagVocabularyPort;
import dev.persefonia.contentpublishing.application.query.PublicContentBySourceQuery;
import dev.persefonia.contentpublishing.application.query.PublicContentNavigation.SeriesLink;
import dev.persefonia.contentpublishing.application.query.PublicContentNavigation;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentStatus;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import dev.persefonia.contentpublishing.domain.content.port.ContentItemRepository;
import dev.persefonia.contentpublishing.domain.model.series.port.SeriesRepository;
import java.util.Objects;
import java.util.Optional;

public final class PublicContentNavigationQueryService {
    private final ContentItemRepository items;
    private final ContentTagVocabularyPort vocabulary;
    private final SeriesRepository series;
    private final ContentPublicRouteFactory contentRoutes;
    private final SeriesPublicRouteFactory seriesRoutes;

    public PublicContentNavigationQueryService(ContentItemRepository items, ContentTagVocabularyPort vocabulary,
            SeriesRepository series, ContentPublicRouteFactory contentRoutes, SeriesPublicRouteFactory seriesRoutes) {
        this.items = Objects.requireNonNull(items);
        this.vocabulary = Objects.requireNonNull(vocabulary);
        this.series = Objects.requireNonNull(series);
        this.contentRoutes = Objects.requireNonNull(contentRoutes);
        this.seriesRoutes = Objects.requireNonNull(seriesRoutes);
    }

    public Optional<PublicContentNavigation> find(PublicContentBySourceQuery query) {
        Objects.requireNonNull(query, "query");
        return items.findById(ContentId.from(query.contentItemId()))
                .filter(item -> item.status() == ContentStatus.PUBLISHED
                        && item.visibility() != ContentVisibility.PRIVATE && item.renderSnapshot().isPresent())
                .filter(item -> item.slug().map(slug -> contentRoutes.publicUrl(item.type(), item.language(), slug)
                        .value().equals(query.expectedPublicPath())).orElse(false))
                .map(item -> new PublicContentNavigation(
                        vocabulary.findByIds(item.tagIds()).stream().filter(tag -> !tag.archived()).toList(),
                        series.findActiveContaining(item.id(), item.language()).stream()
                                .map(value -> new SeriesLink(value.id().value(), value.title().value(), value.slug().value(),
                                        seriesRoutes.publicUrl(value.language(), value.slug()).value())).toList()));
    }
}
