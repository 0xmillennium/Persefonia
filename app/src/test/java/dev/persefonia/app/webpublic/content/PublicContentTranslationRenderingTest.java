package dev.persefonia.app.webpublic.content;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PublicContentTranslationRenderingTest extends PublicContentMvcTestSupport {
    private static final Instant NOW = Instant.parse("2026-06-12T12:30:00Z");

    @Autowired PublicContentTestTranslationGroupRepository translationGroups;

    @BeforeEach
    void resetTranslationGroups() {
        translationGroups.reset();
    }

    @Test
    void publicContentPageRendersVisibleTranslationLink() throws Exception {
        ContentItem turkish = PublicContentTestItems.publishedPublic(
                ContentType.ARTICLE, ContentLanguage.TR, "articles", "merhaba");
        ContentItem english = PublicContentTestItems.publishedPublic(
                ContentType.ARTICLE, ContentLanguage.EN, "articles", "hello");
        addProjected(turkish, "/tr/articles/merhaba");
        addProjected(english, "/en/articles/hello");
        translationGroups.add(group(turkish, english));

        mockMvc.perform(get("/tr/articles/merhaba"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<nav class=\"public-translations\" aria-label=\"Translations\">")))
                .andExpect(content().string(containsString("Available in:")))
                .andExpect(content().string(containsString("<a href=\"/en/articles/hello\" hreflang=\"en\" lang=\"en\" title=\"Public &lt;Title>\">English</a>")));
    }

    @Test
    void publicContentPageRendersHreflangAlternates() throws Exception {
        ContentItem turkish = PublicContentTestItems.publishedPublic(
                ContentType.ARTICLE, ContentLanguage.TR, "articles", "merhaba");
        ContentItem english = PublicContentTestItems.publishedPublic(
                ContentType.ARTICLE, ContentLanguage.EN, "articles", "hello");
        addProjected(turkish, "/tr/articles/merhaba");
        addProjected(english, "/en/articles/hello");
        translationGroups.add(group(turkish, english));

        mockMvc.perform(get("/tr/articles/merhaba"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<link rel=\"alternate\" hreflang=\"tr\" href=\"https://0xmillennium.dev/tr/articles/merhaba\">")))
                .andExpect(content().string(containsString("<link rel=\"alternate\" hreflang=\"en\" href=\"https://0xmillennium.dev/en/articles/hello\">")))
                .andExpect(content().string(not(containsString("hreflang=\"x-" + "default\""))));
    }

    @Test
    void publicContentPageDoesNotRenderTranslationLinksWhenNoEligibleTargets() throws Exception {
        ContentItem turkish = PublicContentTestItems.publishedPublic(
                ContentType.ARTICLE, ContentLanguage.TR, "articles", "merhaba");
        addProjected(turkish, "/tr/articles/merhaba");
        translationGroups.add(group(turkish));

        mockMvc.perform(get("/tr/articles/merhaba"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("public-translations"))))
                .andExpect(content().string(not(containsString("hreflang"))));
    }

    @Test
    void publicUnlistedContentPageDoesNotRenderTranslationLinksOrHreflang() throws Exception {
        ContentItem current = PublicContentTestItems.publishedUnlisted("direct");
        ContentItem english = PublicContentTestItems.publishedPublic(
                ContentType.ARTICLE, ContentLanguage.EN, "articles", "hello");
        addProjected(current, "/tr/articles/direct");
        addProjected(english, "/en/articles/hello");
        translationGroups.add(group(current, english));

        mockMvc.perform(get("/tr/articles/direct"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<meta name=\"robots\" content=\"noindex\">")))
                .andExpect(content().string(not(containsString("public-translations"))))
                .andExpect(content().string(not(containsString("hreflang"))));
    }

    @Test
    void translationLinkUsesEscapedTitleAndExpectedLanguageLabel() throws Exception {
        ContentItem turkish = PublicContentTestItems.publishedPublic(
                ContentType.ARTICLE, ContentLanguage.TR, "articles", "merhaba");
        ContentItem english = PublicContentTestItems.publishedPublic(
                ContentType.ARTICLE, ContentLanguage.EN, "articles", "hello");
        addProjected(turkish, "/tr/articles/merhaba");
        addProjected(english, "/en/articles/hello");
        translationGroups.add(group(turkish, english));

        mockMvc.perform(get("/tr/articles/merhaba"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("title=\"Public &lt;Title>\"")))
                .andExpect(content().string(containsString(">English</a>")))
                .andExpect(content().string(not(containsString("title=\"Public <Title>\""))));
    }

    @Test
    void translationLinksDoNotExposeUnlistedPrivateDraftUnpublishedArchivedMissingSnapshotOrStaleTargets()
            throws Exception {
        assertTargetNotExposed(PublicContentTestItems.publishedUnlisted("target-unlisted"));
        assertTargetNotExposed(PublicContentTestItems.publishedPrivate("target-private"));
        assertTargetNotExposed(PublicContentTestItems.draft("target-draft"));
        assertTargetNotExposed(PublicContentTestItems.unpublished("target-unpublished"));
        assertTargetNotExposed(PublicContentTestItems.archived("target-archived"));
        assertTargetNotExposed(PublicContentTestItems.publishedWithoutSnapshot("target-missing-snapshot"));
        assertTargetNotExposed(contentItem(
                ContentStatus.PUBLISHED,
                ContentVisibility.PUBLIC,
                ContentLanguage.TR,
                "target-current",
                snapshot(),
                "target-old"));
    }

    private void assertTargetNotExposed(ContentItem target) throws Exception {
        resetPublicContentMvcFakes();
        translationGroups.reset();
        ContentItem current = PublicContentTestItems.publishedPublic(
                ContentType.ARTICLE, ContentLanguage.EN, "articles", "current");
        addProjected(current, "/en/articles/current");
        items.add(target);
        translationGroups.add(group(current, target));

        mockMvc.perform(get("/en/articles/current"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("public-translations"))))
                .andExpect(content().string(not(containsString("hreflang"))))
                .andExpect(content().string(not(containsString(target.slug().orElseThrow().value()))));
    }

    private static TranslationGroup group(ContentItem... items) {
        return TranslationGroup.rehydrate(
                TranslationGroupId.newId(),
                Arrays.stream(items)
                        .map(item -> new TranslationGroupEntry(
                                TranslationGroupEntryId.newId(),
                                item.id(),
                                item.language(),
                                item.type(),
                                NOW))
                        .toList(),
                NOW,
                NOW,
                Version.initial());
    }

    private static ContentItem contentItem(
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
                Title.of("Target " + slug),
                Summary.of("Target summary"),
                MarkdownSource.of("# Target"),
                ContentMetadata.withCanonicalPath(CanonicalPath.of("/tr/articles/" + canonicalSlug)),
                renderSnapshot,
                Set.of(),
                status == ContentStatus.PUBLISHED ? NOW : null,
                status == ContentStatus.UNPUBLISHED || status == ContentStatus.ARCHIVED ? NOW.plusSeconds(60) : null,
                NOW.minusSeconds(60),
                NOW,
                Version.initial());
    }

    private static ContentRenderSnapshot snapshot() {
        return ContentRenderSnapshot.of(
                RenderedHtml.sanitized("<p>Target rendered</p>"),
                NOW,
                RendererVersion.of("test-renderer"),
                ReadingTime.minutes(2),
                false,
                List.of());
    }
}
