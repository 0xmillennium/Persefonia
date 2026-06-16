package dev.persefonia.contentpublishing.application.service;

import dev.persefonia.contentpublishing.application.discovery.ContentPublicRouteFactory;
import dev.persefonia.contentpublishing.application.query.PublicContentBySourceQuery;
import dev.persefonia.contentpublishing.application.query.PublicContentLookupResult;
import dev.persefonia.contentpublishing.domain.content.CanonicalPath;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.content.ContentStatus;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import dev.persefonia.contentpublishing.domain.content.port.ContentItemRepository;
import java.util.Objects;

public final class PublicContentBySourceQueryHandler {
    private final ContentItemRepository items;
    private final ContentPublicRouteFactory routeFactory;

    public PublicContentBySourceQueryHandler(ContentItemRepository items, ContentPublicRouteFactory routeFactory) {
        this.items = Objects.requireNonNull(items, "items");
        this.routeFactory = Objects.requireNonNull(routeFactory, "routeFactory");
    }

    public PublicContentLookupResult lookup(PublicContentBySourceQuery query) {
        Objects.requireNonNull(query, "query");
        return items.findById(ContentId.from(query.contentItemId()))
                .filter(this::isPublicDirectRouteContent)
                .filter(item -> currentPublicPathMatches(item, query.expectedPublicPath()))
                .flatMap(item -> PublicContentReadModelMapper.toPageResult(
                        item, CanonicalPath.of(query.expectedPublicPath())))
                .<PublicContentLookupResult>map(PublicContentLookupResult.Found::new)
                .orElseGet(PublicContentLookupResult.NotFound::new);
    }

    private boolean isPublicDirectRouteContent(ContentItem item) {
        return item.status() == ContentStatus.PUBLISHED
                && item.visibility() != ContentVisibility.PRIVATE
                && item.renderSnapshot().isPresent();
    }

    private boolean currentPublicPathMatches(ContentItem item, String expectedPublicPath) {
        return item.slug()
                .map(slug -> routeFactory.publicUrl(item.type(), item.language(), slug).value())
                .filter(expectedPublicPath::equals)
                .isPresent();
    }
}
