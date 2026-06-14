package dev.persefonia.contentpublishing.application.service;

import dev.persefonia.contentpublishing.application.query.PublicContentHeadingResult;
import dev.persefonia.contentpublishing.application.query.PublicContentPageResult;
import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.content.ContentRenderSnapshot;
import dev.persefonia.contentpublishing.domain.content.RenderedHeading;
import java.util.Comparator;
import java.util.Optional;

final class PublicContentReadModelMapper {
    private PublicContentReadModelMapper() {
    }

    static Optional<PublicContentPageResult> toPageResult(ContentItem item) {
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
                .map(PublicContentReadModelMapper::toHeadingResult)
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
