package dev.persefonia.app.webpublic.content;

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
import dev.persefonia.contentpublishing.domain.content.Title;
import dev.persefonia.contentpublishing.domain.content.Version;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class PublicContentTestItems {
    public static final Instant NOW = Instant.parse("2026-06-12T12:00:00Z");

    private PublicContentTestItems() {
    }

    public static ContentItem publishedPublic(ContentType type, ContentLanguage language, String collection, String slug) {
        return published(type, language, ContentVisibility.PUBLIC, collection, slug);
    }

    public static ContentItem publishedPublicWithMermaid(String slug) {
        ContentItem item = completeDraft(ContentType.ARTICLE, ContentLanguage.TR, ContentVisibility.PUBLIC, "articles", slug);
        item.publish(mermaidSnapshot(), NOW.plusSeconds(1));
        return item;
    }

    public static ContentItem publishedPublicWithoutHeadings(String slug) {
        ContentItem item = completeDraft(ContentType.ARTICLE, ContentLanguage.TR, ContentVisibility.PUBLIC, "articles", slug);
        item.publish(snapshotWithoutHeadings(), NOW.plusSeconds(1));
        return item;
    }

    public static ContentItem publishedUnlisted(String slug) {
        return published(ContentType.ARTICLE, ContentLanguage.TR, ContentVisibility.UNLISTED, "articles", slug);
    }

    public static ContentItem publishedPrivate(String slug) {
        return published(ContentType.ARTICLE, ContentLanguage.TR, ContentVisibility.PRIVATE, "articles", slug);
    }

    public static ContentItem draft(String slug) {
        return completeDraft(ContentType.ARTICLE, ContentLanguage.TR, ContentVisibility.PUBLIC, "articles", slug);
    }

    public static ContentItem unpublished(String slug) {
        ContentItem item = publishedPublic(ContentType.ARTICLE, ContentLanguage.TR, "articles", slug);
        item.unpublish(NOW.plusSeconds(2));
        return item;
    }

    public static ContentItem archived(String slug) {
        ContentItem item = publishedPublic(ContentType.ARTICLE, ContentLanguage.TR, "articles", slug);
        item.archive(NOW.plusSeconds(2));
        return item;
    }

    public static ContentItem publishedWithoutSnapshot(String slug) {
        return ContentItem.rehydrate(
                ContentId.newId(),
                ContentType.ARTICLE,
                ContentStatus.PUBLISHED,
                ContentVisibility.PUBLIC,
                ContentLanguage.TR,
                Slug.of(slug),
                Title.of("Snapshot missing"),
                Summary.of("Snapshot missing summary"),
                MarkdownSource.of("# Snapshot missing"),
                ContentMetadata.withCanonicalPath(CanonicalPath.of("/tr/articles/" + slug)),
                null,
                Set.of(),
                NOW,
                null,
                NOW.minusSeconds(60),
                NOW,
                Version.initial());
    }

    private static ContentItem published(
            ContentType type,
            ContentLanguage language,
            ContentVisibility visibility,
            String collection,
            String slug) {
        ContentItem item = completeDraft(type, language, visibility, collection, slug);
        item.publish(snapshot(), NOW.plusSeconds(1));
        return item;
    }

    private static ContentItem completeDraft(
            ContentType type,
            ContentLanguage language,
            ContentVisibility visibility,
            String collection,
            String slug) {
        ContentItem item = ContentItem.createDraft(ContentId.newId(), type, visibility, language, NOW.minusSeconds(60));
        item.changeSlug(Slug.of(slug), NOW.minusSeconds(50));
        item.changeTitle(Title.of("Public <Title>"), NOW.minusSeconds(49));
        item.changeSummary(Summary.of("Summary <strong>escaped</strong>"), NOW.minusSeconds(48));
        item.changeMarkdownSource(MarkdownSource.of("# Public title"), NOW.minusSeconds(47));
        item.changeMetadata(
                ContentMetadata.of(
                        SeoTitle.of("SEO <Title>"),
                        SeoDescription.of("SEO <description>"),
                        CanonicalPath.of("/" + language.name().toLowerCase() + "/" + collection + "/" + slug),
                        OpenGraphTitle.of("OG <Title>"),
                        OpenGraphDescription.of("OG <description>"),
                        null),
                NOW.minusSeconds(46));
        return item;
    }

    private static ContentRenderSnapshot snapshot() {
        return ContentRenderSnapshot.of(
                RenderedHtml.sanitized("<h2 id=\"heading-escaped\">Persisted heading</h2><p><strong>Persisted HTML</strong></p>"),
                NOW,
                RendererVersion.of("test-renderer"),
                ReadingTime.minutes(4),
                false,
                List.of(RenderedHeading.of(2, "Heading <Escaped>", "heading-escaped", 1)));
    }

    private static ContentRenderSnapshot mermaidSnapshot() {
        return ContentRenderSnapshot.of(
                RenderedHtml.sanitized("<pre><code class=\"language-mermaid\">graph TD; A-->B;</code></pre>"),
                NOW,
                RendererVersion.of("test-renderer"),
                ReadingTime.minutes(4),
                true,
                List.of(RenderedHeading.of(2, "Heading <Escaped>", "heading-escaped", 1)));
    }

    private static ContentRenderSnapshot snapshotWithoutHeadings() {
        return ContentRenderSnapshot.of(
                RenderedHtml.sanitized("<p><strong>Persisted HTML</strong></p>"),
                NOW,
                RendererVersion.of("test-renderer"),
                ReadingTime.minutes(4),
                false,
                List.of());
    }
}
