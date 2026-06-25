package dev.persefonia.webpublic.sitemap;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.discovery.application.contract.DiscoveryLanguage;
import dev.persefonia.discovery.application.index.PublicSitemapEntry;
import dev.persefonia.discovery.application.index.PublicSitemapIndexQueryService;
import dev.persefonia.webpublic.content.PublicCanonicalUrlFactory;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PublicSitemapDocumentServiceTest {
    private static final String BASE_URL = "https://example.test";

    @Test
    void rendersUrlsetNamespaceWithAbsoluteLocValues() {
        PublicSitemapDocumentService service = service(
                staticRoutes(false),
                entry("/en/projects/portfolio", Instant.parse("2026-06-24T10:15:00Z")));

        String xml = service.renderXml();

        assertThat(xml).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        assertThat(xml).contains("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">");
        assertThat(xml).contains("<loc>https://example.test/</loc>");
        assertThat(xml).contains("<loc>https://example.test/en/projects/portfolio</loc>");
        assertThat(xml).contains("<lastmod>2026-06-24</lastmod>");
    }

    @Test
    void escapesSpecialCharactersInLoc() {
        PublicSitemapDocumentService service = service(
                staticRoutes(false),
                entry("/en/projects/a&b", Instant.parse("2026-06-24T10:15:00Z")));

        assertThat(service.renderXml()).contains("<loc>https://example.test/en/projects/a&amp;b</loc>");
    }

    @Test
    void excludesAdminSearchMediaAndCvDownloadPaths() {
        PublicSitemapDocumentService service = service(
                staticRoutes(true),
                entry("/search/results", Instant.parse("2026-06-24T10:15:00Z")),
                entry("/media/assets/1/variants/large", Instant.parse("2026-06-24T10:15:00Z")),
                entry("/admin/secret", Instant.parse("2026-06-24T10:15:00Z")));

        String xml = service.renderXml();

        assertThat(xml).doesNotContain("/search");
        assertThat(xml).doesNotContain("/media/assets");
        assertThat(xml).doesNotContain("/admin");
        assertThat(xml).doesNotContain("/cv/download");
        assertThat(xml).contains("<loc>https://example.test/cv</loc>");
    }

    @Test
    void deduplicatesStaticAndDynamicByAbsoluteUrl() {
        PublicSitemapDocumentService service = service(
                staticRoutes(false),
                entry("/", Instant.parse("2026-06-24T10:15:00Z")));

        long homeOccurrences = service.renderXml().lines()
                .filter(line -> line.contains("<loc>https://example.test/</loc>"))
                .count();

        assertThat(homeOccurrences).isEqualTo(1L);
    }

    @Test
    void omitsLastmodForStaticEntriesAndNeverEmitsPriorityOrChangefreq() {
        PublicSitemapDocumentService service = service(staticRoutes(false));

        String xml = service.renderXml();

        assertThat(xml).doesNotContain("priority");
        assertThat(xml).doesNotContain("changefreq");
        assertThat(xml).doesNotContain("image:");
        assertThat(xml).doesNotContain("hreflang");
        // Home static entry carries no lastmod.
        assertThat(xml).contains("    <loc>https://example.test/</loc>\n  </url>");
    }

    private static PublicSitemapDocumentService service(
            PublicSitemapStaticRouteProvider staticRoutes, PublicSitemapEntry... entries) {
        PublicSitemapIndexQueryService index = limit -> List.of(entries);
        return new PublicSitemapDocumentService(
                index, staticRoutes, new PublicCanonicalUrlFactory(BASE_URL));
    }

    private static PublicSitemapStaticRouteProvider staticRoutes(boolean cv) {
        return new PublicSitemapStaticRouteProvider(() -> cv);
    }

    private static PublicSitemapEntry entry(String publicUrl, Instant lastModified) {
        return new PublicSitemapEntry(
                publicUrl, BASE_URL + publicUrl, DiscoveryLanguage.EN, lastModified);
    }
}
