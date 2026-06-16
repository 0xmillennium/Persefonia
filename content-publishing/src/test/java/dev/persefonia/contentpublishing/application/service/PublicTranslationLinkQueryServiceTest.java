package dev.persefonia.contentpublishing.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.contentpublishing.application.query.PublicContentPageResult;
import dev.persefonia.contentpublishing.application.query.PublicTranslationLinkSet;
import dev.persefonia.contentpublishing.application.support.InMemoryContentItemRepository;
import dev.persefonia.contentpublishing.application.support.InMemoryContentReadModels;
import dev.persefonia.contentpublishing.application.support.InMemorySeriesRepository;
import dev.persefonia.contentpublishing.application.support.InMemoryTranslationGroupRepository;
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
import dev.persefonia.contentpublishing.domain.translation.TranslationGroup;
import dev.persefonia.contentpublishing.domain.translation.TranslationGroupEntry;
import dev.persefonia.contentpublishing.domain.translation.TranslationGroupEntryId;
import dev.persefonia.contentpublishing.domain.translation.TranslationGroupId;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PublicTranslationLinkQueryServiceTest {
    private static final Instant CREATED_AT = Instant.parse("2026-06-12T08:00:00Z");
    private static final Instant PUBLISHED_AT = Instant.parse("2026-06-12T09:00:00Z");

    private final InMemoryContentItemRepository items = new InMemoryContentItemRepository();
    private final InMemoryTranslationGroupRepository translationGroups = new InMemoryTranslationGroupRepository();
    private final PublicTranslationLinkQueryService service = new PublicTranslationLinkQueryService(
            new InMemoryContentReadModels(items, new InMemorySeriesRepository(), translationGroups));

    @Test
    void noLinksWhenContentHasNoTranslationGroup() {
        ContentItem current = publicContent(ContentLanguage.TR, "merhaba");
        items.add(current);

        PublicTranslationLinkSet links = service.linksFor(page(current));

        assertEmpty(links);
    }

    @Test
    void noLinksWhenGroupHasOnlyCurrentContent() {
        ContentItem current = publicContent(ContentLanguage.TR, "merhaba");
        items.add(current);
        translationGroups.add(group(current));

        PublicTranslationLinkSet links = service.linksFor(page(current));

        assertEmpty(links);
    }

    @Test
    void rendersEligiblePublicTranslationTarget() {
        ContentItem current = publicContent(ContentLanguage.TR, "merhaba");
        ContentItem english = publicContent(ContentLanguage.EN, "hello");
        addGroup(current, english);

        PublicTranslationLinkSet links = service.linksFor(page(current));

        assertThat(links.renderVisibleLinks()).isTrue();
        assertThat(links.visibleLinks()).singleElement().satisfies(link -> {
            assertThat(link.language()).isEqualTo("en");
            assertThat(link.label()).isEqualTo("English");
            assertThat(link.title()).isEqualTo("Public hello");
            assertThat(link.publicUrl()).isEqualTo("/en/articles/hello");
            assertThat(link.canonicalUrl()).isEqualTo("https://example.test/en/articles/hello");
        });
    }

    @Test
    void includesSelfHreflangWhenEligibleAlternateExists() {
        ContentItem current = publicContent(ContentLanguage.TR, "merhaba");
        ContentItem english = publicContent(ContentLanguage.EN, "hello");
        addGroup(current, english);

        PublicTranslationLinkSet links = service.linksFor(page(current));

        assertThat(links.renderHreflang()).isTrue();
        assertThat(links.hreflangLinks())
                .extracting(link -> link.languageCode() + " " + link.href())
                .containsExactly(
                        "tr https://example.test/tr/articles/merhaba",
                        "en https://example.test/en/articles/hello");
    }

    @Test
    void doesNotRenderSelfOnlyHreflang() {
        ContentItem current = publicContent(ContentLanguage.TR, "merhaba");
        items.add(current);
        translationGroups.add(group(current));

        PublicTranslationLinkSet links = service.linksFor(page(current));

        assertThat(links.renderHreflang()).isFalse();
        assertThat(links.hreflangLinks()).isEmpty();
    }

    @Test
    void excludesDraftTranslationTarget() {
        assertTargetExcluded(content(ContentStatus.DRAFT, ContentVisibility.PUBLIC, ContentLanguage.EN, "draft", snapshot(), "draft"));
    }

    @Test
    void excludesUnpublishedTranslationTarget() {
        assertTargetExcluded(content(
                ContentStatus.UNPUBLISHED, ContentVisibility.PUBLIC, ContentLanguage.EN, "unpublished", snapshot(), "unpublished"));
    }

    @Test
    void excludesArchivedTranslationTarget() {
        assertTargetExcluded(content(
                ContentStatus.ARCHIVED, ContentVisibility.PUBLIC, ContentLanguage.EN, "archived", snapshot(), "archived"));
    }

    @Test
    void excludesPrivateTranslationTarget() {
        assertTargetExcluded(content(
                ContentStatus.PUBLISHED, ContentVisibility.PRIVATE, ContentLanguage.EN, "private", snapshot(), "private"));
    }

    @Test
    void excludesUnlistedTranslationTarget() {
        assertTargetExcluded(content(
                ContentStatus.PUBLISHED, ContentVisibility.UNLISTED, ContentLanguage.EN, "unlisted", snapshot(), "unlisted"));
    }

    @Test
    void excludesTargetWithoutRenderSnapshot() {
        assertTargetExcluded(content(
                ContentStatus.PUBLISHED, ContentVisibility.PUBLIC, ContentLanguage.EN, "missing-snapshot", null, "missing-snapshot"));
    }

    @Test
    void excludesTargetWithStalePublicPath() {
        assertTargetExcluded(content(
                ContentStatus.PUBLISHED, ContentVisibility.PUBLIC, ContentLanguage.EN, "current-slug", snapshot(), "old-slug"));
    }

    @Test
    void doesNotRenderForCurrentUnlistedContent() {
        ContentItem current = content(
                ContentStatus.PUBLISHED, ContentVisibility.UNLISTED, ContentLanguage.TR, "direct", snapshot(), "direct");
        ContentItem english = publicContent(ContentLanguage.EN, "hello");
        addGroup(current, english);

        PublicTranslationLinkSet links = service.linksFor(page(current));

        assertEmpty(links);
    }

    @Test
    void usesCanonicalUrlsForHreflang() {
        ContentItem current = publicContent(ContentLanguage.TR, "merhaba");
        ContentItem english = publicContent(ContentLanguage.EN, "hello");
        addGroup(current, english);

        PublicTranslationLinkSet links = service.linksFor(page(current));

        assertThat(links.hreflangLinks())
                .extracting(link -> link.href())
                .containsExactly(
                        "https://example.test/tr/articles/merhaba",
                        "https://example.test/en/articles/hello");
    }

    @Test
    void usesPublicUrlsForVisibleLinks() {
        ContentItem current = publicContent(ContentLanguage.TR, "merhaba");
        ContentItem english = publicContent(ContentLanguage.EN, "hello");
        addGroup(current, english);

        PublicTranslationLinkSet links = service.linksFor(page(current));

        assertThat(links.visibleLinks())
                .extracting(link -> link.publicUrl())
                .containsExactly("/en/articles/hello");
    }

    @Test
    void turkishPageLinksToEnglishTranslation() {
        ContentItem turkish = publicContent(ContentLanguage.TR, "merhaba");
        ContentItem english = publicContent(ContentLanguage.EN, "hello");
        addGroup(turkish, english);

        assertThat(service.linksFor(page(turkish)).visibleLinks())
                .extracting(link -> link.language())
                .containsExactly("en");
    }

    @Test
    void englishPageLinksToTurkishTranslation() {
        ContentItem turkish = publicContent(ContentLanguage.TR, "merhaba");
        ContentItem english = publicContent(ContentLanguage.EN, "hello");
        addGroup(turkish, english);

        assertThat(service.linksFor(page(english)).visibleLinks())
                .extracting(link -> link.language())
                .containsExactly("tr");
    }

    @Test
    void bothPagesRenderSelfAndAlternateHreflang() {
        ContentItem turkish = publicContent(ContentLanguage.TR, "merhaba");
        ContentItem english = publicContent(ContentLanguage.EN, "hello");
        addGroup(turkish, english);

        assertThat(service.linksFor(page(turkish)).hreflangLinks())
                .extracting(link -> link.languageCode())
                .containsExactly("tr", "en");
        assertThat(service.linksFor(page(english)).hreflangLinks())
                .extracting(link -> link.languageCode())
                .containsExactly("en", "tr");
    }

    private void assertTargetExcluded(ContentItem target) {
        ContentItem current = publicContent(ContentLanguage.TR, "merhaba");
        addGroup(current, target);

        PublicTranslationLinkSet links = service.linksFor(page(current));

        assertEmpty(links);
    }

    private void addGroup(ContentItem... groupItems) {
        Arrays.stream(groupItems).forEach(items::add);
        translationGroups.add(group(groupItems));
    }

    private static void assertEmpty(PublicTranslationLinkSet links) {
        assertThat(links.renderVisibleLinks()).isFalse();
        assertThat(links.renderHreflang()).isFalse();
        assertThat(links.visibleLinks()).isEmpty();
        assertThat(links.hreflangLinks()).isEmpty();
    }

    private static PublicContentPageResult page(ContentItem item) {
        return PublicContentReadModelMapper.toPageResult(item).orElseThrow();
    }

    private static TranslationGroup group(ContentItem... items) {
        List<TranslationGroupEntry> entries = Arrays.stream(items)
                .map(item -> new TranslationGroupEntry(
                        TranslationGroupEntryId.newId(),
                        item.id(),
                        item.language(),
                        item.type(),
                        CREATED_AT))
                .toList();
        return TranslationGroup.rehydrate(
                TranslationGroupId.newId(),
                entries,
                CREATED_AT,
                CREATED_AT,
                Version.initial());
    }

    private static ContentItem publicContent(ContentLanguage language, String slug) {
        return content(ContentStatus.PUBLISHED, ContentVisibility.PUBLIC, language, slug, snapshot(), slug);
    }

    private static ContentItem content(
            ContentStatus status,
            ContentVisibility visibility,
            ContentLanguage language,
            String slug,
            ContentRenderSnapshot renderSnapshot,
            String canonicalSlug) {
        return ContentItem.rehydrate(
                ContentId.newId(),
                ContentType.ARTICLE,
                status,
                visibility,
                language,
                Slug.of(slug),
                Title.of("Public " + slug),
                Summary.of("Summary for " + slug),
                MarkdownSource.of("# " + slug),
                ContentMetadata.withCanonicalPath(CanonicalPath.of("/" + languageCode(language) + "/articles/" + canonicalSlug)),
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
                RenderedHtml.sanitized("<p>Rendered translation</p>"),
                PUBLISHED_AT,
                RendererVersion.of("test-renderer"),
                ReadingTime.minutes(2),
                false,
                List.of());
    }

    private static String languageCode(ContentLanguage language) {
        return switch (language) {
            case EN -> "en";
            case TR -> "tr";
        };
    }
}
