package dev.persefonia.contentpublishing.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import dev.persefonia.contentpublishing.application.query.PublicContentLookupResult;
import dev.persefonia.contentpublishing.application.query.PublicContentPageResult;
import dev.persefonia.contentpublishing.application.query.PublicContentRouteQuery;
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
import dev.persefonia.contentpublishing.domain.content.port.ContentItemRepository;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PublicContentQueryHandlerTest {
    private static final Instant CREATED_AT = Instant.parse("2026-06-11T08:00:00Z");
    private static final Instant PUBLISHED_AT = Instant.parse("2026-06-11T09:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-06-11T10:00:00Z");
    private static final Slug SLUG = Slug.ofCanonical("public-content");

    private final InMemoryContentItemRepository items = new InMemoryContentItemRepository();
    private final PublicContentQueryHandler handler = new PublicContentQueryHandler(items);

    @Test
    void publishedPublicContentReturnsPublicReadModel() {
        ContentRenderSnapshot snapshot = snapshotWithUnsortedHeadings();
        ContentItem item = content(ContentStatus.PUBLISHED, ContentVisibility.PUBLIC, snapshot);
        items.add(item);

        PublicContentPageResult page = foundPage(handler.lookup(query()));

        assertThat(page.contentId()).isEqualTo(item.id());
        assertThat(page.title()).isEqualTo(Title.of("Public content"));
        assertThat(page.summary()).isEqualTo(Summary.of("Public-safe summary."));
        assertThat(page.slug()).isEqualTo(SLUG);
        assertThat(page.type()).isEqualTo(ContentType.ARTICLE);
        assertThat(page.language()).isEqualTo(ContentLanguage.EN);
        assertThat(page.visibility()).isEqualTo(ContentVisibility.PUBLIC);
        assertThat(page.canonicalPath()).isEqualTo(CanonicalPath.of("/en/articles/public-content"));
        assertThat(page.seoTitle()).contains(SeoTitle.of("SEO public content"));
        assertThat(page.seoDescription()).contains(SeoDescription.of("SEO summary."));
        assertThat(page.openGraphTitle()).contains(OpenGraphTitle.of("OG public content"));
        assertThat(page.openGraphDescription()).contains(OpenGraphDescription.of("OG summary."));
        assertThat(page.publishedAt()).isEqualTo(PUBLISHED_AT);
        assertThat(page.updatedAt()).isEqualTo(UPDATED_AT);
        assertThat(page.renderedHtml()).isEqualTo(snapshot.renderedHtml());
        assertThat(page.readingTime()).isEqualTo(snapshot.readingTime());
        assertThat(page.containsMermaid()).isEqualTo(snapshot.containsMermaid());
        assertThat(page.rendererVersion()).isEqualTo(snapshot.rendererVersion());
        assertThat(page.headings())
                .extracting(heading -> heading.position().value())
                .containsExactly(1, 2);
        assertThat(page.headings())
                .extracting(heading -> heading.anchor().value())
                .containsExactly("first-heading", "second-heading");
    }

    @Test
    void publishedUnlistedContentReturnsPublicReadModelForDirectRoute() {
        items.add(content(ContentStatus.PUBLISHED, ContentVisibility.UNLISTED, snapshotWithUnsortedHeadings()));

        PublicContentPageResult page = foundPage(handler.lookup(query()));

        assertThat(page.visibility()).isEqualTo(ContentVisibility.UNLISTED);
        assertThat(page.renderedHtml()).isEqualTo(RenderedHtml.sanitized("<h1>Persisted public content</h1>"));
    }

    @Test
    void missingContentReturnsNotFound() {
        assertNotFound(handler.lookup(query()));
    }

    @Test
    void draftContentReturnsNotFound() {
        items.add(content(ContentStatus.DRAFT, ContentVisibility.PUBLIC, snapshotWithUnsortedHeadings()));

        assertNotFound(handler.lookup(query()));
    }

    @Test
    void unpublishedContentReturnsNotFound() {
        items.add(content(ContentStatus.UNPUBLISHED, ContentVisibility.PUBLIC, snapshotWithUnsortedHeadings()));

        assertNotFound(handler.lookup(query()));
    }

    @Test
    void archivedContentReturnsNotFound() {
        items.add(content(ContentStatus.ARCHIVED, ContentVisibility.PUBLIC, snapshotWithUnsortedHeadings()));

        assertNotFound(handler.lookup(query()));
    }

    @Test
    void privatePublishedContentReturnsNotFound() {
        items.add(content(ContentStatus.PUBLISHED, ContentVisibility.PRIVATE, snapshotWithUnsortedHeadings()));

        assertNotFound(handler.lookup(query()));
    }

    @Test
    void publishedContentWithoutRenderSnapshotReturnsNotFound() {
        items.add(content(ContentStatus.PUBLISHED, ContentVisibility.PUBLIC, null));

        assertNotFound(handler.lookup(query()));
    }

    @Test
    void handlerRejectsRepositoryReturnedDraft() {
        var leaky = new PublicContentQueryHandler(leakyRepository(
                content(ContentStatus.DRAFT, ContentVisibility.PUBLIC, snapshotWithUnsortedHeadings())));

        assertNotFound(leaky.lookup(query()));
    }

    @Test
    void handlerRejectsRepositoryReturnedPrivatePublished() {
        var leaky = new PublicContentQueryHandler(leakyRepository(
                content(ContentStatus.PUBLISHED, ContentVisibility.PRIVATE, snapshotWithUnsortedHeadings())));

        assertNotFound(leaky.lookup(query()));
    }

    @Test
    void handlerRejectsRepositoryReturnedArchived() {
        var leaky = new PublicContentQueryHandler(leakyRepository(
                content(ContentStatus.ARCHIVED, ContentVisibility.PUBLIC, snapshotWithUnsortedHeadings())));

        assertNotFound(leaky.lookup(query()));
    }

    @Test
    void handlerRejectsRepositoryReturnedPublishedWithoutSnapshot() {
        var leaky = new PublicContentQueryHandler(leakyRepository(
                content(ContentStatus.PUBLISHED, ContentVisibility.PUBLIC, null)));

        assertNotFound(leaky.lookup(query()));
    }

    @Test
    void publicResultDoesNotExposeDraftBody() {
        assertPublicResultDoesNotExpose(
                "Markdown" + "Source",
                "markdown" + "Source",
                "source",
                "raw" + "Markdown");
    }

    @Test
    void publicResultDoesNotExposeRevisionData() {
        assertPublicResultDoesNotExpose("Content" + "Revision");
    }

    @Test
    void publicResultDoesNotExposeAdminIdentity() {
        assertPublicResultDoesNotExpose("Admin" + "IdentityRef");
    }

    @Test
    void publicResultDoesNotExposeUnpublishedAt() {
        assertPublicResultDoesNotExpose("unpublished" + "At");
    }

    @Test
    void publicResultDoesNotExposeOptimisticLock() {
        assertThat(recordComponents(PublicContentPageResult.class))
                .noneMatch(component -> component.getName().equals("version"));
        assertThat(recordComponents(PublicContentPageResult.class))
                .noneMatch(component -> component.getGenericType().getTypeName().endsWith(".Ver" + "sion"));
    }

    @Test
    void queryDoesNotMutateContentItem() {
        ContentItem item = content(ContentStatus.PUBLISHED, ContentVisibility.PUBLIC, snapshotWithUnsortedHeadings());
        Instant updatedAt = item.updatedAt();
        Object version = item.version();
        items.add(item);

        PublicContentLookupResult result = handler.lookup(query());

        assertThat(result).isInstanceOf(PublicContentLookupResult.Found.class);
        assertThat(item.updatedAt()).isEqualTo(updatedAt);
        assertThat(item.version()).isEqualTo(version);
        assertThat(items.saveCount()).isZero();
    }

    @Test
    void queryDoesNotCreateRevision() {
        assertThat(PublicContentQueryHandler.class.getDeclaredFields())
                .extracting(field -> field.getType().getName())
                .noneMatch(type -> type.contains("Content" + "Revision"));
    }

    @Test
    void queryDoesNotEmitEvents() {
        assertThat(PublicContentQueryHandler.class.getDeclaredFields())
                .extracting(field -> field.getType().getName())
                .noneMatch(type -> type.contains("Event"));
    }

    @Test
    void routeQueryRequiresTypeLanguageAndSlug() {
        assertThatNullPointerException()
                .isThrownBy(() -> new PublicContentRouteQuery(null, ContentLanguage.EN, SLUG));
        assertThatNullPointerException()
                .isThrownBy(() -> new PublicContentRouteQuery(ContentType.ARTICLE, null, SLUG));
        assertThatNullPointerException()
                .isThrownBy(() -> new PublicContentRouteQuery(ContentType.ARTICLE, ContentLanguage.EN, null));
    }

    private static PublicContentRouteQuery query() {
        return new PublicContentRouteQuery(ContentType.ARTICLE, ContentLanguage.EN, SLUG);
    }

    private static ContentItem content(
            ContentStatus status,
            ContentVisibility visibility,
            ContentRenderSnapshot renderSnapshot) {
        return ContentItem.rehydrate(
                ContentId.newId(),
                ContentType.ARTICLE,
                status,
                visibility,
                ContentLanguage.EN,
                SLUG,
                Title.of("Public content"),
                Summary.of("Public-safe summary."),
                null,
                metadata(),
                renderSnapshot,
                Set.<TagId>of(),
                status == ContentStatus.PUBLISHED ? PUBLISHED_AT : null,
                status == ContentStatus.UNPUBLISHED || status == ContentStatus.ARCHIVED
                        ? UPDATED_AT
                        : null,
                CREATED_AT,
                UPDATED_AT,
                ContentItem.createDraft(
                        ContentId.newId(),
                        ContentType.ARTICLE,
                        ContentVisibility.PUBLIC,
                        ContentLanguage.EN,
                        CREATED_AT).version());
    }

    private static ContentMetadata metadata() {
        return ContentMetadata.of(
                SeoTitle.of("SEO public content"),
                SeoDescription.of("SEO summary."),
                CanonicalPath.of("/en/articles/public-content"),
                OpenGraphTitle.of("OG public content"),
                OpenGraphDescription.of("OG summary."),
                null);
    }

    private static ContentRenderSnapshot snapshotWithUnsortedHeadings() {
        return ContentRenderSnapshot.of(
                RenderedHtml.sanitized("<h1>Persisted public content</h1>"),
                PUBLISHED_AT,
                RendererVersion.of("renderer-2026.06"),
                ReadingTime.minutes(4),
                true,
                List.of(
                        RenderedHeading.of(2, "Second heading", "second-heading", 2),
                        RenderedHeading.of(1, "First heading", "first-heading", 1)));
    }

    private static PublicContentPageResult foundPage(PublicContentLookupResult result) {
        assertThat(result).isInstanceOf(PublicContentLookupResult.Found.class);
        return ((PublicContentLookupResult.Found) result).page();
    }

    private static void assertNotFound(PublicContentLookupResult result) {
        assertThat(result).isInstanceOf(PublicContentLookupResult.NotFound.class);
    }

    private static void assertPublicResultDoesNotExpose(String... forbiddenTokens) {
        assertThat(recordComponents(PublicContentPageResult.class))
                .noneSatisfy(component -> assertThat(component.getName()).isIn((Object[]) forbiddenTokens));
        assertThat(recordComponents(PublicContentPageResult.class))
                .noneSatisfy(component -> assertThat(component.getGenericType().getTypeName())
                        .containsAnyOf(forbiddenTokens));
    }

    private static RecordComponent[] recordComponents(Class<?> type) {
        return type.getRecordComponents();
    }

    private static ContentItemRepository leakyRepository(ContentItem item) {
        return new ContentItemRepository() {
            private final Map<ContentId, ContentItem> itemsById = new LinkedHashMap<>(Map.of(item.id(), item));

            @Override
            public ContentItem save(ContentItem item) {
                itemsById.put(item.id(), item);
                return item;
            }

            @Override
            public Optional<ContentItem> findById(ContentId id) {
                return Optional.ofNullable(itemsById.get(id));
            }

            @Override
            public Optional<ContentItem> findBySlugAndTypeAndLanguage(
                    Slug slug,
                    ContentType type,
                    ContentLanguage language) {
                return itemsById.values().stream()
                        .filter(candidate -> candidate.slug().filter(slug::equals).isPresent()
                                && candidate.type() == type
                                && candidate.language() == language)
                        .findFirst();
            }

            @Override
            public Optional<ContentItem> findPublishedByRoute(
                    ContentType type,
                    Slug slug,
                    ContentLanguage language) {
                return findBySlugAndTypeAndLanguage(slug, type, language);
            }

            @Override
            public List<ContentItem> findDrafts() {
                return findByStatus(ContentStatus.DRAFT);
            }

            @Override
            public List<ContentItem> findByStatus(ContentStatus status) {
                return itemsById.values().stream()
                        .filter(candidate -> candidate.status() == status)
                        .toList();
            }

            @Override
            public boolean existsSlugInNamespace(ContentType type, ContentLanguage language, Slug slug) {
                return findBySlugAndTypeAndLanguage(slug, type, language).isPresent();
            }
        };
    }
}
