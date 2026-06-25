package dev.persefonia.webpublic.feed;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.discovery.application.contract.DiscoveryLanguage;
import dev.persefonia.discovery.application.index.PublicFeedEntry;
import dev.persefonia.discovery.application.index.PublicFeedIndexQueryService;
import dev.persefonia.webpublic.content.PublicCanonicalUrlFactory;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PublicAtomFeedDocumentServiceTest {
    private static final String BASE_URL = "https://example.test";
    private static final String TITLE = "Example Feed";
    private static final String SUBTITLE = "Latest updates";

    @Test
    void rendersAtomNamespaceAndFeedLevelFields() {
        PublicAtomFeedDocumentService service = service(
                entry("/en/articles/hello", "Hello", "A summary",
                        Instant.parse("2026-06-24T12:34:56Z"), Instant.parse("2026-06-24T12:34:56Z")));

        String xml = service.renderXml();

        assertThat(xml).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        assertThat(xml).contains("<feed xmlns=\"http://www.w3.org/2005/Atom\">");
        assertThat(xml).contains("<id>https://example.test/feed.xml</id>");
        assertThat(xml).contains("<title>Example Feed</title>");
        assertThat(xml).contains("<subtitle>Latest updates</subtitle>");
        assertThat(xml).contains("<updated>2026-06-24T12:34:56Z</updated>");
        assertThat(xml).contains("<link rel=\"self\" href=\"https://example.test/feed.xml\"/>");
        assertThat(xml).contains("<link rel=\"alternate\" href=\"https://example.test/\"/>");
        assertThat(xml).endsWith("</feed>\n");
    }

    @Test
    void rendersEntryFieldsAsAbsoluteSummaryOnlyEntries() {
        PublicAtomFeedDocumentService service = service(
                entry("/en/articles/hello", "Hello", "A summary",
                        Instant.parse("2026-06-20T08:00:00Z"), Instant.parse("2026-06-24T12:34:56Z")));

        String xml = service.renderXml();

        assertThat(xml).contains("<entry>");
        assertThat(xml).contains("<id>https://example.test/en/articles/hello</id>");
        assertThat(xml).contains("<title>Hello</title>");
        assertThat(xml).contains("<link rel=\"alternate\" href=\"https://example.test/en/articles/hello\"/>");
        assertThat(xml).contains("<published>2026-06-20T08:00:00Z</published>");
        assertThat(xml).contains("<updated>2026-06-24T12:34:56Z</updated>");
        assertThat(xml).contains("<summary>A summary</summary>");
    }

    @Test
    void feedUpdatedUsesNewestEntryUpdatedTimestamp() {
        PublicAtomFeedDocumentService service = service(
                entry("/en/articles/old", "Old", "Old summary",
                        Instant.parse("2026-06-01T00:00:00Z"), Instant.parse("2026-06-10T00:00:00Z")),
                entry("/en/articles/new", "New", "New summary",
                        Instant.parse("2026-06-02T00:00:00Z"), Instant.parse("2026-06-23T09:00:00Z")));

        String xml = service.renderXml();

        // Feed-level updated is the newest entry updated value.
        assertThat(xml).contains("  <updated>2026-06-23T09:00:00Z</updated>\n");
    }

    @Test
    void usesConfiguredBaseUrlForAllAbsoluteUrls() {
        PublicAtomFeedDocumentService service = service(
                entry("/tr/notes/note", "Not", "Ozet",
                        Instant.parse("2026-06-24T12:00:00Z"), Instant.parse("2026-06-24T12:00:00Z")));

        String xml = service.renderXml();

        assertThat(xml).contains("https://example.test/feed.xml");
        assertThat(xml).contains("https://example.test/tr/notes/note");
        assertThat(xml).doesNotContain("evil.example");
        assertThat(xml).doesNotContain("attacker.test");
    }

    @Test
    void escapesXmlSpecialCharactersInTitleSummaryAndAttributes() {
        PublicAtomFeedDocumentService service = service(
                entry("/en/articles/a&b", "A & B <c> \"d\" 'e'", "Sum & <mary>",
                        Instant.parse("2026-06-24T12:00:00Z"), Instant.parse("2026-06-24T12:00:00Z")));

        String xml = service.renderXml();

        assertThat(xml).contains("<title>A &amp; B &lt;c&gt; &quot;d&quot; &apos;e&apos;</title>");
        assertThat(xml).contains("<summary>Sum &amp; &lt;mary&gt;</summary>");
        assertThat(xml).contains("href=\"https://example.test/en/articles/a&amp;b\"");
        assertThat(xml).doesNotContain("<c>");
    }

    @Test
    void rendersSummaryOnlyWithoutContentEnclosuresOrRssElements() {
        PublicAtomFeedDocumentService service = service(
                entry("/en/articles/hello", "Hello", "A summary",
                        Instant.parse("2026-06-24T12:00:00Z"), Instant.parse("2026-06-24T12:00:00Z")));

        String xml = service.renderXml();

        assertThat(xml).doesNotContain("<content");
        assertThat(xml).doesNotContain("enclosure");
        assertThat(xml).doesNotContain("<rss");
        assertThat(xml).doesNotContain("<channel");
        assertThat(xml).doesNotContain("<item>");
        assertThat(xml).doesNotContain("CDATA");
    }

    @Test
    void rendersValidEmptyFeedWithDeterministicUpdated() {
        PublicAtomFeedDocumentService service = service();

        String xml = service.renderXml();

        assertThat(xml).contains("<feed xmlns=\"http://www.w3.org/2005/Atom\">");
        assertThat(xml).contains("<updated>2024-01-01T00:00:00Z</updated>");
        assertThat(xml).doesNotContain("<entry>");
        assertThat(xml).endsWith("</feed>\n");
    }

    @Test
    void excludesUnsafeAndIneligibleRouteFamiliesEvenFromSource() {
        PublicAtomFeedDocumentService service = service(
                entry("/search", "Search", "s", now(), now()),
                entry("/sitemap.xml", "Sitemap", "s", now(), now()),
                entry("/robots.txt", "Robots", "s", now(), now()),
                entry("/feed.xml", "Feed", "s", now(), now()),
                entry("/rss.xml", "Rss", "s", now(), now()),
                entry("/atom.xml", "Atom", "s", now(), now()),
                entry("/cv", "Cv", "s", now(), now()),
                entry("/cv/download", "CvDownload", "s", now(), now()),
                entry("/media/assets/1/variants/large", "Media", "s", now(), now()),
                entry("/admin/secret", "Admin", "s", now(), now()),
                entry("/oauth2/authorization/x", "OAuth", "s", now(), now()),
                entry("/actuator/health", "Actuator", "s", now(), now()),
                entry("/preview/x", "Preview", "s", now(), now()),
                entry("/tags/topic", "Tags", "s", now(), now()),
                entry("/series/x", "Series", "s", now(), now()),
                entry("/tr/tags/topic", "TrTags", "s", now(), now()),
                entry("/en/series/x", "EnSeries", "s", now(), now()),
                entry("PROJECT", "/en/projects/portfolio", "Project", "s", now(), now()),
                entry("/en/articles/keep", "Keep", "kept", now(), now()));

        String xml = service.renderXml();

        assertThat(xml).doesNotContain("/search");
        assertThat(xml).doesNotContain("/sitemap.xml");
        assertThat(xml).doesNotContain("/robots.txt");
        assertThat(xml).doesNotContain("/rss.xml");
        assertThat(xml).doesNotContain("/atom.xml");
        assertThat(xml).doesNotContain("/cv");
        assertThat(xml).doesNotContain("/media");
        assertThat(xml).doesNotContain("/admin");
        assertThat(xml).doesNotContain("/oauth2");
        assertThat(xml).doesNotContain("/actuator");
        assertThat(xml).doesNotContain("/preview");
        assertThat(xml).doesNotContain("/tags/");
        assertThat(xml).doesNotContain("/series/");
        assertThat(xml).doesNotContain("/projects/");
        // The feed self route never appears as an entry id.
        assertThat(xml).doesNotContain("<id>https://example.test/feed.xml</id>\n    <title>Feed</title>");
        // Only the eligible article survives.
        assertThat(xml).contains("<id>https://example.test/en/articles/keep</id>");
    }

    @Test
    void rejectsAbsoluteExternalUrlsAndControlCharacterPaths() {
        assertThat(PublicAtomFeedDocumentService.isSafePublicPath("https://evil.test/x")).isFalse();
        assertThat(PublicAtomFeedDocumentService.isSafePublicPath("/x://y")).isFalse();
        assertThat(PublicAtomFeedDocumentService.isSafePublicPath("relative/path")).isFalse();
        assertThat(PublicAtomFeedDocumentService.isSafePublicPath("/path\u0007control")).isFalse();
        assertThat(PublicAtomFeedDocumentService.isSafePublicPath("/en/articles/ok")).isTrue();
    }

    @Test
    void deduplicatesEntriesByAbsoluteUrl() {
        PublicAtomFeedDocumentService service = service(
                entry("/en/articles/dup", "First", "first", now(), now()),
                entry("/en/articles/dup", "Second", "second", now(), now()));

        long occurrences = service.renderXml().lines()
                .filter(line -> line.contains("<id>https://example.test/en/articles/dup</id>"))
                .count();

        assertThat(occurrences).isEqualTo(1L);
    }

    private static Instant now() {
        return Instant.parse("2026-06-24T12:00:00Z");
    }

    private static PublicAtomFeedDocumentService service(PublicFeedEntry... entries) {
        PublicFeedIndexQueryService index = limit -> List.of(entries);
        return new PublicAtomFeedDocumentService(
                index, new PublicCanonicalUrlFactory(BASE_URL), TITLE, SUBTITLE);
    }

    private static PublicFeedEntry entry(
            String publicUrl, String title, String summary, Instant publishedAt, Instant updatedAt) {
        return entry("ARTICLE", publicUrl, title, summary, publishedAt, updatedAt);
    }

    private static PublicFeedEntry entry(
            String sourceType, String publicUrl, String title, String summary, Instant publishedAt, Instant updatedAt) {
        return new PublicFeedEntry(
                sourceType,
                "id-" + Math.abs(publicUrl.hashCode()),
                DiscoveryLanguage.EN,
                publicUrl,
                BASE_URL + publicUrl,
                title,
                summary,
                publishedAt,
                updatedAt);
    }
}
