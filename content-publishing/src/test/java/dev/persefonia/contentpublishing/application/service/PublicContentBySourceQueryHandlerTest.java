package dev.persefonia.contentpublishing.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import dev.persefonia.contentpublishing.application.discovery.ContentPublicRouteFactory;
import dev.persefonia.contentpublishing.application.query.PublicContentBySourceQuery;
import dev.persefonia.contentpublishing.application.query.PublicContentLookupResult;
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
import dev.persefonia.contentpublishing.domain.content.Title;
import dev.persefonia.contentpublishing.domain.content.Version;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PublicContentBySourceQueryHandlerTest {
    private static final Instant CREATED_AT = Instant.parse("2026-06-12T08:00:00Z");
    private static final Instant PUBLISHED_AT = Instant.parse("2026-06-12T09:00:00Z");
    private static final Slug SLUG = Slug.of("source-route");

    private final InMemoryContentItemRepository items = new InMemoryContentItemRepository();
    private final PublicContentBySourceQueryHandler handler =
            new PublicContentBySourceQueryHandler(items, new ContentPublicRouteFactory());

    @Test
    void publishedPublicWithSnapshotAndExpectedPathReturnsFound() {
        ContentItem item = content(ContentStatus.PUBLISHED, ContentVisibility.PUBLIC, snapshot(), SLUG);
        items.add(item);

        PublicContentLookupResult result = handler.lookup(query(item, "/en/articles/source-route"));

        assertThat(result).isInstanceOfSatisfying(PublicContentLookupResult.Found.class, found -> {
            assertThat(found.page().contentId()).isEqualTo(item.id());
            assertThat(found.page().renderedHtml()).isEqualTo(RenderedHtml.sanitized("<p>Persisted HTML</p>"));
        });
    }

    @Test
    void publishedUnlistedWithSnapshotAndExpectedPathReturnsFound() {
        ContentItem item = content(ContentStatus.PUBLISHED, ContentVisibility.UNLISTED, snapshot(), SLUG);
        items.add(item);

        PublicContentLookupResult result = handler.lookup(query(item, "/en/articles/source-route"));

        assertThat(result).isInstanceOfSatisfying(PublicContentLookupResult.Found.class,
                found -> assertThat(found.page().visibility()).isEqualTo(ContentVisibility.UNLISTED));
    }

    @Test
    void privateDraftUnpublishedArchivedMissingSnapshotAndMissingContentReturnNotFound() {
        assertNotFound(content(ContentStatus.PUBLISHED, ContentVisibility.PRIVATE, snapshot(), SLUG));
        assertNotFound(content(ContentStatus.DRAFT, ContentVisibility.PUBLIC, snapshot(), SLUG));
        assertNotFound(content(ContentStatus.UNPUBLISHED, ContentVisibility.PUBLIC, snapshot(), SLUG));
        assertNotFound(content(ContentStatus.ARCHIVED, ContentVisibility.PUBLIC, snapshot(), SLUG));
        assertNotFound(content(ContentStatus.PUBLISHED, ContentVisibility.PUBLIC, null, SLUG));
        assertThat(handler.lookup(new PublicContentBySourceQuery(UUID.randomUUID(), "/en/articles/source-route")))
                .isInstanceOf(PublicContentLookupResult.NotFound.class);
    }

    @Test
    void expectedPathMismatchReturnsNotFound() {
        ContentItem item = content(ContentStatus.PUBLISHED, ContentVisibility.PUBLIC, snapshot(), Slug.of("current-route"));
        items.add(item);

        assertThat(handler.lookup(query(item, "/en/articles/old-route")))
                .isInstanceOf(PublicContentLookupResult.NotFound.class);
    }

    @Test
    void queryRejectsMissingOrNonPathOnlyExpectedPath() {
        assertThatNullPointerException()
                .isThrownBy(() -> new PublicContentBySourceQuery(null, "/en/articles/source-route"));
        assertThatNullPointerException()
                .isThrownBy(() -> new PublicContentBySourceQuery(UUID.randomUUID(), null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PublicContentBySourceQuery(UUID.randomUUID(), ""));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PublicContentBySourceQuery(UUID.randomUUID(), "https://example.test/path"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PublicContentBySourceQuery(UUID.randomUUID(), "//example.test/path"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PublicContentBySourceQuery(UUID.randomUUID(), "/en/articles/source-route?preview=true"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PublicContentBySourceQuery(UUID.randomUUID(), "/en/articles/source-route#heading"));
    }

    @Test
    void queryHandlerDoesNotRenderMarkdown() {
        assertThat(PublicContentBySourceQueryHandler.class.getDeclaredFields())
                .extracting(field -> field.getType().getName())
                .noneMatch(type -> type.contains("Markdown"));
    }

    private void assertNotFound(ContentItem item) {
        items.add(item);
        assertThat(handler.lookup(query(item, "/en/articles/source-route")))
                .isInstanceOf(PublicContentLookupResult.NotFound.class);
    }

    private static PublicContentBySourceQuery query(ContentItem item, String expectedPath) {
        return new PublicContentBySourceQuery(item.id().value(), expectedPath);
    }

    private static ContentItem content(
            ContentStatus status,
            ContentVisibility visibility,
            ContentRenderSnapshot renderSnapshot,
            Slug slug) {
        return ContentItem.rehydrate(
                ContentId.newId(),
                ContentType.ARTICLE,
                status,
                visibility,
                ContentLanguage.EN,
                slug,
                Title.of("Public content"),
                Summary.of("Public-safe summary."),
                MarkdownSource.of("# Draft source is not rendered here"),
                ContentMetadata.withCanonicalPath(CanonicalPath.of("/en/articles/" + slug.value())),
                renderSnapshot,
                Set.of(),
                status == ContentStatus.PUBLISHED ? PUBLISHED_AT : null,
                status == ContentStatus.UNPUBLISHED || status == ContentStatus.ARCHIVED ? PUBLISHED_AT.plusSeconds(60) : null,
                CREATED_AT,
                PUBLISHED_AT.plusSeconds(120),
                Version.initial());
    }

    private static ContentRenderSnapshot snapshot() {
        return ContentRenderSnapshot.of(
                RenderedHtml.sanitized("<p>Persisted HTML</p>"),
                PUBLISHED_AT,
                RendererVersion.of("test-renderer"),
                ReadingTime.minutes(3),
                false,
                List.of());
    }
}
