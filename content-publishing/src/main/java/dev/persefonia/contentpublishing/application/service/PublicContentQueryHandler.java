package dev.persefonia.contentpublishing.application.service;

import dev.persefonia.contentpublishing.application.query.PublicContentLookupResult;
import dev.persefonia.contentpublishing.application.query.PublicContentRouteQuery;
import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.content.ContentStatus;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import dev.persefonia.contentpublishing.domain.content.port.ContentItemRepository;
import java.util.Objects;

public final class PublicContentQueryHandler {
    private final ContentItemRepository items;

    public PublicContentQueryHandler(ContentItemRepository items) {
        this.items = Objects.requireNonNull(items, "items");
    }

    public PublicContentLookupResult lookup(PublicContentRouteQuery query) {
        Objects.requireNonNull(query, "query");
        return items.findPublishedByRoute(query.type(), query.slug(), query.language())
                .filter(this::isPublicDirectRouteContent)
                .flatMap(PublicContentReadModelMapper::toPageResult)
                .<PublicContentLookupResult>map(PublicContentLookupResult.Found::new)
                .orElseGet(PublicContentLookupResult.NotFound::new);
    }

    private boolean isPublicDirectRouteContent(ContentItem item) {
        return item.status() == ContentStatus.PUBLISHED
                && item.visibility() != ContentVisibility.PRIVATE;
    }
}
