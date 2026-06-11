package dev.persefonia.app.contentpublishing.persistence;

import dev.persefonia.contentpublishing.domain.content.AssetId;
import dev.persefonia.contentpublishing.domain.content.CanonicalPath;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentMetadata;
import dev.persefonia.contentpublishing.domain.content.ContentRenderSnapshot;
import dev.persefonia.contentpublishing.domain.content.ContentStatus;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import dev.persefonia.contentpublishing.domain.content.MarkdownSource;
import dev.persefonia.contentpublishing.domain.content.OpenGraphDescription;
import dev.persefonia.contentpublishing.domain.content.OpenGraphTitle;
import dev.persefonia.contentpublishing.domain.content.ReadingTime;
import dev.persefonia.contentpublishing.domain.content.RenderedHeading;
import dev.persefonia.contentpublishing.domain.content.RenderedHtml;
import dev.persefonia.contentpublishing.domain.content.RendererVersion;
import dev.persefonia.contentpublishing.domain.content.SeoDescription;
import dev.persefonia.contentpublishing.domain.content.SeoTitle;
import dev.persefonia.contentpublishing.domain.content.Slug;
import dev.persefonia.contentpublishing.domain.content.Summary;
import dev.persefonia.contentpublishing.domain.content.TagId;
import dev.persefonia.contentpublishing.domain.content.Title;
import java.time.Instant;
import java.util.List;
import java.util.Set;

final class ContentItemRepositoryTestFixtures {
    static final Instant NOW = Instant.parse("2026-06-11T09:00:00Z");

    private ContentItemRepositoryTestFixtures() {
    }

    static ContentItem incompleteDraft() {
        return ContentItem.createDraft(
                ContentId.newId(),
                ContentType.ARTICLE,
                ContentVisibility.PRIVATE,
                ContentLanguage.EN,
                NOW);
    }

    static ContentItem completeDraft(String slug) {
        ContentItem item = ContentItem.createDraft(
                ContentId.newId(),
                ContentType.ARTICLE,
                ContentVisibility.PUBLIC,
                ContentLanguage.EN,
                NOW);
        item.changeSlug(Slug.ofCanonical(slug), NOW.plusSeconds(1));
        item.changeTitle(Title.of("Title " + slug), NOW.plusSeconds(2));
        item.changeSummary(Summary.of("Summary for " + slug), NOW.plusSeconds(3));
        item.changeMarkdownSource(MarkdownSource.of("# " + slug), NOW.plusSeconds(4));
        item.changeMetadata(metadata(slug), NOW.plusSeconds(5));
        return item;
    }

    static ContentItem completeDraft(String slug, ContentType type, ContentLanguage language) {
        ContentItem item = ContentItem.createDraft(ContentId.newId(), type, ContentVisibility.PUBLIC, language, NOW);
        item.changeSlug(Slug.ofCanonical(slug), NOW.plusSeconds(1));
        item.changeTitle(Title.of("Title " + slug), NOW.plusSeconds(2));
        item.changeSummary(Summary.of("Summary for " + slug), NOW.plusSeconds(3));
        item.changeMarkdownSource(MarkdownSource.of("# " + slug), NOW.plusSeconds(4));
        item.changeMetadata(metadata(slug), NOW.plusSeconds(5));
        return item;
    }

    static ContentItem published(String slug, ContentVisibility visibility) {
        ContentItem item = completeDraft(slug);
        item.changeVisibility(visibility, NOW.plusSeconds(6));
        item.publish(snapshot("renderer-v1", true, headings("intro", "details")), NOW.plusSeconds(7));
        return item;
    }

    static ContentItem unpublished(String slug) {
        ContentItem item = published(slug, ContentVisibility.PUBLIC);
        item.unpublish(NOW.plusSeconds(8));
        return item;
    }

    static ContentItem archived(String slug) {
        ContentItem item = published(slug, ContentVisibility.PUBLIC);
        item.archive(NOW.plusSeconds(8));
        return item;
    }

    static ContentItem withoutRenderSnapshot(ContentItem item) {
        return copy(item, null, item.tagIds());
    }

    static ContentItem withTags(ContentItem item, Set<TagId> tagIds) {
        return copy(item, item.renderSnapshot().orElse(null), tagIds);
    }

    static ContentRenderSnapshot snapshot(String rendererVersion, boolean containsMermaid, List<RenderedHeading> headings) {
        return ContentRenderSnapshot.of(
                RenderedHtml.sanitized("<article><h1>" + rendererVersion + "</h1></article>"),
                NOW.plusSeconds(30),
                RendererVersion.of(rendererVersion),
                ReadingTime.minutes(4),
                containsMermaid,
                headings);
    }

    static List<RenderedHeading> headings(String firstAnchor, String secondAnchor) {
        return List.of(
                RenderedHeading.of(2, "Second", secondAnchor, 2),
                RenderedHeading.of(1, "First", firstAnchor, 1));
    }

    static ContentMetadata metadata(String slug) {
        return ContentMetadata.of(
                SeoTitle.of("SEO " + slug),
                SeoDescription.of("SEO description " + slug),
                CanonicalPath.of("/articles/" + slug),
                OpenGraphTitle.of("OG " + slug),
                OpenGraphDescription.of("OG description " + slug),
                AssetId.newId());
    }

    private static ContentItem copy(ContentItem item, ContentRenderSnapshot snapshot, Set<TagId> tagIds) {
        return ContentItem.rehydrate(
                item.id(),
                item.type(),
                item.status(),
                item.visibility(),
                item.language(),
                item.slug().orElse(null),
                item.title().orElse(null),
                item.summary().orElse(null),
                item.markdownSource().orElse(null),
                item.metadata(),
                snapshot,
                tagIds,
                item.publishedAt().orElse(null),
                item.unpublishedAt().orElse(null),
                item.createdAt(),
                item.updatedAt(),
                item.version());
    }
}
