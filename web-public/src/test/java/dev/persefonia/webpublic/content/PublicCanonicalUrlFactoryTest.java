package dev.persefonia.webpublic.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class PublicCanonicalUrlFactoryTest {
    @Test
    void buildsAbsoluteUrlFromHttpsBaseUrl() {
        PublicCanonicalUrlFactory factory = new PublicCanonicalUrlFactory("https://example.test");

        assertThat(factory.canonicalUrl("/sitemap.xml")).isEqualTo("https://example.test/sitemap.xml");
    }

    @Test
    void allowsHttpBaseUrl() {
        PublicCanonicalUrlFactory factory = new PublicCanonicalUrlFactory("http://localhost:8080");

        assertThat(factory.canonicalUrl("/robots.txt")).isEqualTo("http://localhost:8080/robots.txt");
    }

    @Test
    void normalizesTrailingSlash() {
        PublicCanonicalUrlFactory factory = new PublicCanonicalUrlFactory("https://example.test/");

        assertThat(factory.canonicalUrl("/search")).isEqualTo("https://example.test/search");
    }

    @Test
    void rejectsMissingScheme() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PublicCanonicalUrlFactory("example.test"));
    }

    @Test
    void rejectsUnsupportedScheme() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PublicCanonicalUrlFactory("ftp://example.test"));
    }

    @Test
    void rejectsQuery() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PublicCanonicalUrlFactory("https://example.test/?x=1"));
    }

    @Test
    void rejectsFragment() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PublicCanonicalUrlFactory("https://example.test/#frag"));
    }

    @Test
    void rejectsBlankBaseUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PublicCanonicalUrlFactory("  "));
    }

    @Test
    void rejectsPathNotStartingWithSlash() {
        PublicCanonicalUrlFactory factory = new PublicCanonicalUrlFactory("https://example.test");

        assertThatIllegalArgumentException().isThrownBy(() -> factory.canonicalUrl("search"));
    }
}
