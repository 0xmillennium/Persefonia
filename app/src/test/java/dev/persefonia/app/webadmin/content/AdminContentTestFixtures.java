package dev.persefonia.app.webadmin.content;

import dev.persefonia.contentpublishing.domain.content.CanonicalPath;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentMetadata;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import dev.persefonia.contentpublishing.domain.content.MarkdownSource;
import dev.persefonia.contentpublishing.domain.content.Slug;
import dev.persefonia.contentpublishing.domain.content.Summary;
import dev.persefonia.contentpublishing.domain.content.Title;
import java.time.Instant;

final class AdminContentTestFixtures {
    static final Instant CREATED = Instant.parse("2026-06-12T08:00:00Z");

    private AdminContentTestFixtures() {
    }

    static ContentItem completeDraft() {
        ContentItem item = ContentItem.createDraft(
                ContentId.newId(), ContentType.ARTICLE, ContentVisibility.PRIVATE, ContentLanguage.EN, CREATED);
        item.changeSlug(Slug.of("admin-draft"), CREATED.plusSeconds(1));
        item.changeTitle(Title.of("Admin draft"), CREATED.plusSeconds(1));
        item.changeSummary(Summary.of("Admin draft summary"), CREATED.plusSeconds(1));
        item.changeMarkdownSource(
                MarkdownSource.of("# Safe preview\n<script>alert(1)</script>\n<img src=x onerror=alert(1)>"),
                CREATED.plusSeconds(1));
        item.changeMetadata(
                ContentMetadata.withCanonicalPath(CanonicalPath.of("/articles/admin-draft")),
                CREATED.plusSeconds(1));
        return item;
    }

    static ContentItem unpublished() {
        ContentItem item = completeDraft();
        item.publish(dev.persefonia.contentpublishing.domain.content.ContentRenderSnapshot.of(
                dev.persefonia.contentpublishing.domain.content.RenderedHtml.sanitized("<h1>old</h1>"),
                CREATED.plusSeconds(2),
                dev.persefonia.contentpublishing.domain.content.RendererVersion.of("test"),
                dev.persefonia.contentpublishing.domain.content.ReadingTime.minutes(1),
                false,
                java.util.List.of()), CREATED.plusSeconds(2));
        item.unpublish(CREATED.plusSeconds(3));
        return item;
    }

    static ContentItem archived() {
        ContentItem item = completeDraft();
        item.archive(CREATED.plusSeconds(2));
        return item;
    }
}
