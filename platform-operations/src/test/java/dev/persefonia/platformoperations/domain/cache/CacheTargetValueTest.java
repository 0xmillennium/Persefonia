package dev.persefonia.platformoperations.domain.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class CacheTargetValueTest {
    @Test
    void acceptsCanonicalPublicRelativePaths() {
        List.of("/", "/tr/articles/example", "/en/projects/persefonia", "/feed.xml", "/sitemap.xml", "/robots.txt",
                        "/administrator")
                .forEach(value -> assertThat(CacheTargetValue.url(value).value()).isEqualTo(value));
    }

    @Test
    void rejectsNonCanonicalAndSensitivePaths() {
        List.of("https://example.com/foo", "http://example.com/foo", "//example.com/foo", "/admin", "/admin/content",
                        "/oauth2/authorization/authelia", "/login", "/logout", "/actuator", "/actuator/prometheus",
                        "/foo?preview", "/foo#fragment", "/foo/../admin", "/foo/./bar", "/foo\\bar", "/foo//bar",
                        "/%61dmin", "/contains space", "/trailing/")
                .forEach(value -> assertThatThrownBy(() -> CacheTargetValue.url(value))
                        .as(value).isInstanceOf(CacheInvalidationValidationException.class));
    }

    @Test
    void cacheTagsUseBoundedProviderIndependentGrammar() {
        List.of("content:550e8400-e29b-41d4-a716-446655440000",
                        "project:550e8400-e29b-41d4-a716-446655440000", "site:public-documents")
                .forEach(value -> assertThat(CacheTargetValue.cacheTag(value).value()).isEqualTo(value));

        List.of("", " ", "Upper", "wildcard*", "with space", "query?x", "path/value", "control\u0007",
                        "a".repeat(129))
                .forEach(value -> assertThatThrownBy(() -> CacheTargetValue.cacheTag(value))
                        .as(value).isInstanceOf(CacheInvalidationValidationException.class));
    }
}
