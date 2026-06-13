package dev.persefonia.contentpublishing.application.service;

import dev.persefonia.contentpublishing.application.query.PublicContentHeadingResult;
import dev.persefonia.contentpublishing.application.query.PublicContentLookupResult;
import dev.persefonia.contentpublishing.application.query.PublicContentPageResult;
import dev.persefonia.contentpublishing.application.query.PublicContentRouteQuery;
import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.content.ContentRenderSnapshot;
import dev.persefonia.contentpublishing.domain.content.ContentStatus;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import dev.persefonia.contentpublishing.domain.content.RenderedHeading;
import dev.persefonia.contentpublishing.domain.content.port.ContentItemRepository;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

public final class PublicContentQueryHandler {
    private final ContentItemRepository items;

    public PublicContentQueryHandler(ContentItemRepository items) {
        this.items = Objects.requireNonNull(items, "items");
    }

    public PublicContentLookupResult lookup(PublicContentRouteQuery query) {
        Objects.requireNonNull(query, "query");
        return items.findPublishedByRoute(query.type(), query.slug(), query.language())
                .filter(this::isPublicDirectRouteContent)
                .flatMap(this::toPageResult)
                .<PublicContentLookupResult>map(PublicContentLookupResult.Found::new)
                .orElseGet(PublicContentLookupResult.NotFound::new);
    }

    private boolean isPublicDirectRouteContent(ContentItem item) {
        return item.status() == ContentStatus.PUBLISHED
                && item.visibility() != ContentVisibility.PRIVATE;
    }

    private Optional<PublicContentPageResult> toPageResult(ContentItem item) {
        return item.slug().flatMap(slug ->
                item.title().flatMap(title ->
                        item.summary().flatMap(summary ->
                                item.metadata().canonicalPath().flatMap(canonicalPath ->
                                        item.publishedAt().flatMap(publishedAt ->
                                                item.renderSnapshot().map(snapshot -> new PublicContentPageResult(
                                                        item.id(),
                                                        item.type(),
                                                        item.language(),
                                                        item.visibility(),
                                                        slug,
                                                        title,
                                                        summary,
                                                        canonicalPath,
                                                        item.metadata().seoTitle(),
                                                        item.metadata().seoDescription(),
                                                        item.metadata().openGraphTitle(),
                                                        item.metadata().openGraphDescription(),
                                                        publishedAt,
                                                        item.updatedAt(),
                                                        snapshot.renderedHtml(),
                                                        snapshot.rendererVersion(),
                                                        snapshot.readingTime(),
                                                        snapshot.containsMermaid(),
                                                        headingsFrom(snapshot))))))));
    }

    private static java.util.List<PublicContentHeadingResult> headingsFrom(ContentRenderSnapshot snapshot) {
        return snapshot.headings().stream()
                .sorted(Comparator.comparingInt(heading -> heading.position().value()))
                .map(PublicContentQueryHandler::toHeadingResult)
                .toList();
    }

    private static PublicContentHeadingResult toHeadingResult(RenderedHeading heading) {
        return new PublicContentHeadingResult(
                heading.level(),
                heading.text(),
                heading.anchor(),
                heading.position());
    }
}
