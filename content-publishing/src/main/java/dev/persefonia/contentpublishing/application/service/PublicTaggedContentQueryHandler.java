package dev.persefonia.contentpublishing.application.service;

import dev.persefonia.contentpublishing.application.discovery.ContentPublicRouteFactory;
import dev.persefonia.contentpublishing.application.query.PublicTaggedContentItem;
import dev.persefonia.contentpublishing.application.query.PublicTaggedContentQuery;
import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.content.port.ContentItemRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class PublicTaggedContentQueryHandler {
    private final ContentItemRepository items;
    private final ContentPublicRouteFactory routeFactory;

    public PublicTaggedContentQueryHandler(ContentItemRepository items, ContentPublicRouteFactory routeFactory) {
        this.items = Objects.requireNonNull(items, "items");
        this.routeFactory = Objects.requireNonNull(routeFactory, "routeFactory");
    }

    public List<PublicTaggedContentItem> list(PublicTaggedContentQuery query) {
        Objects.requireNonNull(query, "query");
        return items.findByAssignedTagId(query.tagId()).stream()
                .filter(ContentItem::isListedPublicly)
                .filter(item -> item.language() == query.language())
                .filter(item -> item.renderSnapshot().isPresent())
                .filter(this::currentPublicPathIsValid)
                .sorted(Comparator.comparing((ContentItem item) -> item.publishedAt().orElseThrow())
                        .reversed()
                        .thenComparing(item -> item.id().value()))
                .limit(query.limit())
                .map(this::toResult)
                .toList();
    }

    private boolean currentPublicPathIsValid(ContentItem item) {
        return item.slug()
                .map(slug -> routeFactory.publicUrl(item.type(), item.language(), slug).value())
                .filter(path -> item.metadata().canonicalPath().map(canonical -> canonical.value().equals(path)).orElse(false))
                .isPresent();
    }

    private PublicTaggedContentItem toResult(ContentItem item) {
        String publicUrl = routeFactory.publicUrl(item.type(), item.language(), item.slug().orElseThrow()).value();
        return new PublicTaggedContentItem(
                item.title().orElseThrow().value(),
                item.summary().orElseThrow().value(),
                publicUrl,
                item.metadata().canonicalPath().orElseThrow().value(),
                item.type().name(),
                item.publishedAt().orElseThrow(),
                item.language().name());
    }
}
