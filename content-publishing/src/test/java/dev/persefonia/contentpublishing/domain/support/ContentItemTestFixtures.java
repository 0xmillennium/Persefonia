package dev.persefonia.contentpublishing.domain.support;

import dev.persefonia.contentpublishing.domain.content.CanonicalPath;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentMetadata;
import dev.persefonia.contentpublishing.domain.content.ContentRenderSnapshot;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import dev.persefonia.contentpublishing.domain.content.MarkdownSource;
import dev.persefonia.contentpublishing.domain.content.ReadingTime;
import dev.persefonia.contentpublishing.domain.content.RenderedHeading;
import dev.persefonia.contentpublishing.domain.content.RenderedHtml;
import dev.persefonia.contentpublishing.domain.content.RendererVersion;
import dev.persefonia.contentpublishing.domain.content.Slug;
import dev.persefonia.contentpublishing.domain.content.Summary;
import dev.persefonia.contentpublishing.domain.content.Title;
import java.time.Instant;
import java.util.List;

public final class ContentItemTestFixtures {
    public static final Instant CREATED_AT = Instant.parse("2026-06-10T08:00:00Z");
    public static final Instant EDITED_AT = Instant.parse("2026-06-10T09:00:00Z");
    public static final Instant PUBLISHED_AT = Instant.parse("2026-06-10T10:00:00Z");

    private ContentItemTestFixtures() {
    }

    public static ContentItem draft() {
        return draft(ContentVisibility.PUBLIC);
    }

    public static ContentItem draft(ContentVisibility visibility) {
        return ContentItem.createDraft(
                ContentId.newId(),
                ContentType.ARTICLE,
                visibility,
                ContentLanguage.EN,
                CREATED_AT);
    }

    public static ContentItem completeDraft() {
        return completeDraft(ContentVisibility.PUBLIC);
    }

    public static ContentItem completeDraft(ContentVisibility visibility) {
        ContentItem item = draft(visibility);
        complete(item);
        return item;
    }

    public static ContentItem published(ContentVisibility visibility) {
        ContentItem item = completeDraft(visibility);
        item.publish(renderSnapshot(), PUBLISHED_AT);
        return item;
    }

    public static void complete(ContentItem item) {
        item.changeSlug(slug(), EDITED_AT);
        item.changeTitle(title(), EDITED_AT);
        item.changeSummary(summary(), EDITED_AT);
        item.changeMarkdownSource(markdownSource(), EDITED_AT);
        item.changeMetadata(metadataWithCanonicalPath(), EDITED_AT);
    }

    public static Slug slug() {
        return Slug.ofCanonical("content-baseline");
    }

    public static Title title() {
        return Title.of("Content baseline");
    }

    public static Summary summary() {
        return Summary.of("A concise summary for the content baseline.");
    }

    public static MarkdownSource markdownSource() {
        return MarkdownSource.of("# Content baseline");
    }

    public static ContentMetadata metadataWithCanonicalPath() {
        return ContentMetadata.withCanonicalPath(CanonicalPath.of("/articles/content-baseline"));
    }

    public static ContentRenderSnapshot renderSnapshot() {
        return ContentRenderSnapshot.of(
                RenderedHtml.sanitized("<h1>Content baseline</h1>"),
                PUBLISHED_AT,
                RendererVersion.of("renderer-1"),
                ReadingTime.minutes(3),
                false,
                List.of(RenderedHeading.of(1, "Content baseline", "content-baseline", 0)));
    }
}
