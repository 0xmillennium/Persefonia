package dev.persefonia.contentpublishing.application.service;

import dev.persefonia.contentpublishing.application.discovery.ContentPublicRouteFactory;
import dev.persefonia.contentpublishing.application.query.PublicContentLookupResult;
import dev.persefonia.contentpublishing.application.query.PublicContentRouteQuery;
import dev.persefonia.contentpublishing.domain.content.CanonicalPath;
import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.content.ContentStatus;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import dev.persefonia.contentpublishing.domain.content.port.ContentItemRepository;
import java.util.Objects;

public final class PublicContentQueryHandler {
    private final ContentItemRepository items;
    private final ContentPublicRouteFactory routeFactory;

    public PublicContentQueryHandler(ContentItemRepository items, ContentPublicRouteFactory routeFactory) {
        this.items = Objects.requireNonNull(items, "items");
        this.routeFactory = Objects.requireNonNull(routeFactory, "routeFactory");
    }

    public PublicContentLookupResult lookup(PublicContentRouteQuery query) {
        Objects.requireNonNull(query, "query");
        return items.findPublishedByRoute(query.type(), query.slug(), query.language())
                .filter(this::isPublicDirectRouteContent)
                .flatMap(item -> PublicContentReadModelMapper.toPageResult(
                        item, CanonicalPath.of(routeFactory.publicUrl(query.type(), query.language(), query.slug()).value())))
                .<PublicContentLookupResult>map(PublicContentLookupResult.Found::new)
                .orElseGet(PublicContentLookupResult.NotFound::new);
    }

    private boolean isPublicDirectRouteContent(ContentItem item) {
        return item.status() == ContentStatus.PUBLISHED
                && item.visibility() != ContentVisibility.PRIVATE;
    }
}
