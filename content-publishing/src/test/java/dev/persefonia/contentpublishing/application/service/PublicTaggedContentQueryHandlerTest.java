package dev.persefonia.contentpublishing.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.contentpublishing.application.discovery.ContentPublicRouteFactory;
import dev.persefonia.contentpublishing.application.query.PublicTaggedContentQuery;
import dev.persefonia.contentpublishing.application.support.InMemoryContentItemRepository;
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
import dev.persefonia.contentpublishing.domain.content.ReadingTime;
import dev.persefonia.contentpublishing.domain.content.RenderedHtml;
import dev.persefonia.contentpublishing.domain.content.RendererVersion;
import dev.persefonia.contentpublishing.domain.content.Slug;
import dev.persefonia.contentpublishing.domain.content.Summary;
import dev.persefonia.contentpublishing.domain.content.TagId;
import dev.persefonia.contentpublishing.domain.content.Title;
import dev.persefonia.contentpublishing.domain.content.Version;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PublicTaggedContentQueryHandlerTest {
    private static final Instant NOW = Instant.parse("2026-06-15T10:00:00Z");
    private static final TagId TAG = TagId.newId();
    private final InMemoryContentItemRepository items = new InMemoryContentItemRepository();
    private final PublicTaggedContentQueryHandler handler =
            new PublicTaggedContentQueryHandler(items, new ContentPublicRouteFactory());

    @Test
    void listsOnlySameLanguagePublishedPublicCurrentPathContent() {
        add(content(ContentStatus.PUBLISHED, ContentVisibility.PUBLIC, ContentLanguage.EN, true, false, "listed"));
        add(content(ContentStatus.PUBLISHED, ContentVisibility.UNLISTED, ContentLanguage.EN, true, false, "unlisted"));
        add(content(ContentStatus.PUBLISHED, ContentVisibility.PRIVATE, ContentLanguage.EN, true, false, "private"));
        add(content(ContentStatus.DRAFT, ContentVisibility.PUBLIC, ContentLanguage.EN, true, false, "draft"));
        add(content(ContentStatus.UNPUBLISHED, ContentVisibility.PUBLIC, ContentLanguage.EN, true, false, "unpublished"));
        add(content(ContentStatus.ARCHIVED, ContentVisibility.PUBLIC, ContentLanguage.EN, true, false, "archived"));
        add(content(ContentStatus.PUBLISHED, ContentVisibility.PUBLIC, ContentLanguage.TR, true, false, "turkish"));
        add(content(ContentStatus.PUBLISHED, ContentVisibility.PUBLIC, ContentLanguage.EN, false, false, "no-snapshot"));
        add(content(ContentStatus.PUBLISHED, ContentVisibility.PUBLIC, ContentLanguage.EN, true, true, "stale"));

        var results = handler.list(new PublicTaggedContentQuery(TAG, ContentLanguage.EN, 50));

        assertThat(results).singleElement().satisfies(item -> {
            assertThat(item.title()).isEqualTo("listed");
            assertThat(item.publicUrl()).isEqualTo("/en/articles/listed");
        });
    }

    private void add(ContentItem item) {
        items.add(item);
    }

    private static ContentItem content(
            ContentStatus status,
            ContentVisibility visibility,
            ContentLanguage language,
            boolean snapshot,
            boolean stale,
            String slug) {
        String currentPath = "/" + language.name().toLowerCase() + "/articles/" + slug;
        return ContentItem.rehydrate(
                ContentId.newId(),
                ContentType.ARTICLE,
                status,
                visibility,
                language,
                Slug.of(slug),
                Title.of(slug),
                Summary.of(slug + " summary"),
                MarkdownSource.of("# " + slug),
                ContentMetadata.withCanonicalPath(CanonicalPath.of(stale ? currentPath + "-old" : currentPath)),
                snapshot ? snapshot() : null,
                Set.of(TAG),
                status == ContentStatus.DRAFT ? null : NOW,
                status == ContentStatus.UNPUBLISHED || status == ContentStatus.ARCHIVED ? NOW.plusSeconds(1) : null,
                NOW.minusSeconds(60),
                NOW.plusSeconds(2),
                Version.initial());
    }

    private static ContentRenderSnapshot snapshot() {
        return ContentRenderSnapshot.of(
                RenderedHtml.sanitized("<p>rendered</p>"),
                NOW,
                RendererVersion.of("test"),
                ReadingTime.minutes(1),
                false,
                List.of());
    }
}
