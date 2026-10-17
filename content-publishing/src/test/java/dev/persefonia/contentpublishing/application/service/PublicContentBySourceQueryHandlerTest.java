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
    private static final UUID COVER = UUID.fromString("f4ec3d17-63cc-4a91-883c-9a390c9f8888");

    @Test
    void galleryTagsUseTheApplicationVocabularyOnlyAfterPublicationEligibility() {
        var tagId = dev.persefonia.contentpublishing.domain.content.TagId.newId();
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var vocabulary = new dev.persefonia.contentpublishing.application.port.ContentTagVocabularyPort() {
            public List<dev.persefonia.contentpublishing.application.port.AssignableTagOption> findAssignableTags() { return List.of(); }
            public List<dev.persefonia.contentpublishing.application.port.ReferencedTagDetails> findByIds(Set<dev.persefonia.contentpublishing.domain.content.TagId> ids) {
                calls.incrementAndGet();
                return List.of(new dev.persefonia.contentpublishing.application.port.ReferencedTagDetails(tagId, "Engineering", "engineering", false));
            }
            public dev.persefonia.contentpublishing.application.port.TagAssignmentValidation validateAssignments(
                    Set<dev.persefonia.contentpublishing.domain.content.TagId> before, Set<dev.persefonia.contentpublishing.domain.content.TagId> after) {
                throw new UnsupportedOperationException();
            }
        };
        var withVocabulary = new PublicContentBySourceQueryHandler(items, new ContentPublicRouteFactory(), vocabulary);
        var privateItem = content(ContentStatus.PUBLISHED, ContentVisibility.PRIVATE, snapshot(), SLUG);
        items.add(privateItem);
        assertThat(withVocabulary.galleryMetadata(query(privateItem, "/en/articles/source-route"))).isEmpty();
        assertThat(calls).hasValue(0);
        var publicItem = content(ContentStatus.PUBLISHED, ContentVisibility.PUBLIC, snapshot(), SLUG);
        items.add(publicItem);
        assertThat(withVocabulary.galleryMetadata(query(publicItem, "/en/articles/source-route"))).get()
                .extracting(metadata -> metadata.tags()).isEqualTo(List.of("Engineering"));
        assertThat(calls).hasValue(1);
    }

    private final InMemoryContentItemRepository items = new InMemoryContentItemRepository();
    private final PublicContentBySourceQueryHandler handler =
            new PublicContentBySourceQueryHandler(items, new ContentPublicRouteFactory());

    @Test
    void galleryCoverRequiresPublishedListedRenderableContentAndCurrentPath() {
        var eligible = content(ContentStatus.PUBLISHED, ContentVisibility.PUBLIC, snapshot(), SLUG);
        items.add(eligible);
        assertThat(handler.galleryCover(query(eligible, "/en/articles/source-route"))).contains(COVER.toString());
        assertThat(handler.galleryCover(query(eligible, "/en/articles/stale-route"))).isEmpty();
        for (var status : ContentStatus.values()) {
            for (var visibility : ContentVisibility.values()) {
                if (status == ContentStatus.PUBLISHED && visibility == ContentVisibility.PUBLIC) continue;
                var excluded = content(status, visibility, snapshot(), SLUG);
                items.add(excluded);
                assertThat(handler.galleryCover(query(excluded, "/en/articles/source-route"))).isEmpty();
            }
        }
        var noSnapshot = content(ContentStatus.PUBLISHED, ContentVisibility.PUBLIC, null, SLUG);
        items.add(noSnapshot);
        assertThat(handler.galleryCover(query(noSnapshot, "/en/articles/source-route"))).isEmpty();
    }

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
    void canonicalPathUsesExpectedCurrentPublicPathWhenStoredMetadataIsStale() {
        ContentItem item = content(
                ContentStatus.PUBLISHED,
                ContentVisibility.PUBLIC,
                snapshot(),
                SLUG,
                CanonicalPath.of("/en/articles/old-route"));
        items.add(item);

        PublicContentLookupResult result = handler.lookup(query(item, "/en/articles/source-route"));

        assertThat(result).isInstanceOfSatisfying(PublicContentLookupResult.Found.class,
                found -> assertThat(found.page().canonicalPath())
                        .isEqualTo(CanonicalPath.of("/en/articles/source-route")));
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

    @Test
    void relatedNavigationUsesOnlyEligibleCurrentContentAndActiveSameLanguageSeries() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var vocabulary = new dev.persefonia.contentpublishing.application.port.ContentTagVocabularyPort() {
            public List<dev.persefonia.contentpublishing.application.port.AssignableTagOption> findAssignableTags() { return List.of(); }
            public List<dev.persefonia.contentpublishing.application.port.ReferencedTagDetails> findByIds(Set<dev.persefonia.contentpublishing.domain.content.TagId> ids) {
                calls.incrementAndGet();
                return List.of(new dev.persefonia.contentpublishing.application.port.ReferencedTagDetails(
                        dev.persefonia.contentpublishing.domain.content.TagId.newId(), "Engineering", "engineering", false));
            }
            public dev.persefonia.contentpublishing.application.port.TagAssignmentValidation validateAssignments(
                    Set<dev.persefonia.contentpublishing.domain.content.TagId> before, Set<dev.persefonia.contentpublishing.domain.content.TagId> after) {
                throw new UnsupportedOperationException();
            }
        };
        var series = new dev.persefonia.contentpublishing.application.support.InMemorySeriesRepository();
        var navigation = new PublicContentNavigationQueryService(items, vocabulary, series,
                new ContentPublicRouteFactory(), new dev.persefonia.contentpublishing.application.discovery.SeriesPublicRouteFactory());
        var item = content(ContentStatus.PUBLISHED, ContentVisibility.PUBLIC, snapshot(), SLUG);
        items.add(item);
        var active = dev.persefonia.contentpublishing.domain.model.series.Series.create(
                dev.persefonia.contentpublishing.domain.model.series.SeriesId.newId(), ContentLanguage.EN,
                dev.persefonia.contentpublishing.domain.model.series.SeriesSlug.of("systems"),
                dev.persefonia.contentpublishing.domain.model.series.SeriesTitle.of("Systems"), null, CREATED_AT);
        active.addEntry(dev.persefonia.contentpublishing.domain.model.series.SeriesEntryId.newId(), item.id(), PUBLISHED_AT);
        series.save(active);
        var result = navigation.find(query(item, "/en/articles/source-route")).orElseThrow();
        assertThat(result.tags()).extracting(tag -> tag.slug()).containsExactly("engineering");
        assertThat(result.series()).extracting(link -> link.publicPath()).containsExactly("/en/series/systems");
        active.archive(PUBLISHED_AT.plusSeconds(1));
        series.save(active);
        assertThat(navigation.find(query(item, "/en/articles/source-route")).orElseThrow().series()).isEmpty();
        int eligibleCalls = calls.get();
        assertThat(navigation.find(query(item, "/en/articles/stale"))).isEmpty();
        for (var status : ContentStatus.values()) {
            if (status == ContentStatus.PUBLISHED) continue;
            var hidden = content(status, ContentVisibility.PUBLIC, snapshot(), SLUG);
            items.add(hidden);
            assertThat(navigation.find(query(hidden, "/en/articles/source-route"))).isEmpty();
        }
        var hidden = content(ContentStatus.PUBLISHED, ContentVisibility.PRIVATE, snapshot(), SLUG);
        items.add(hidden);
        assertThat(navigation.find(query(hidden, "/en/articles/source-route"))).isEmpty();
        assertThat(calls).hasValue(eligibleCalls);
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
        return content(
                status,
                visibility,
                renderSnapshot,
                slug,
                CanonicalPath.of("/en/articles/" + slug.value()));
    }

    private static ContentItem content(
            ContentStatus status,
            ContentVisibility visibility,
            ContentRenderSnapshot renderSnapshot,
            Slug slug,
            CanonicalPath canonicalPath) {
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
                ContentMetadata.of(null, null, canonicalPath, null, null,
                        dev.persefonia.contentpublishing.domain.content.AssetId.from(COVER)),
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
