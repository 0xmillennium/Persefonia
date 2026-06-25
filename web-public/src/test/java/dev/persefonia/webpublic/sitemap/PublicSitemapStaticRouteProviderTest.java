package dev.persefonia.webpublic.sitemap;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PublicSitemapStaticRouteProviderTest {
    @Test
    void includesHomeAndProjectListingsAlways() {
        PublicSitemapStaticRouteProvider provider = new PublicSitemapStaticRouteProvider(() -> false);

        assertThat(provider.staticPaths())
                .containsExactly("/", "/tr/projects", "/en/projects");
    }

    @Test
    void includesCvPageWhenActiveCvExists() {
        PublicSitemapStaticRouteProvider provider = new PublicSitemapStaticRouteProvider(() -> true);

        assertThat(provider.staticPaths())
                .containsExactly("/", "/tr/projects", "/en/projects", "/cv");
    }

    @Test
    void excludesCvPageWhenNoActiveCvExists() {
        PublicSitemapStaticRouteProvider provider = new PublicSitemapStaticRouteProvider(() -> false);

        assertThat(provider.staticPaths()).doesNotContain("/cv");
    }

    @Test
    void neverIncludesCvDownloadSearchOrCrawlerDocuments() {
        PublicSitemapStaticRouteProvider provider = new PublicSitemapStaticRouteProvider(() -> true);

        assertThat(provider.staticPaths())
                .doesNotContain("/cv/download", "/cv/tr/download", "/search", "/sitemap.xml", "/robots.txt");
    }
}
