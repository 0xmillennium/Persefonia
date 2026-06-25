package dev.persefonia.app.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RequestRouteCategoryTest {
    @Test
    void classifiesKnownPublicAndAdminRoutes() {
        assertThat(RequestRouteCategory.classify("/", 200)).isEqualTo(RequestRouteCategory.PUBLIC_HOME);
        assertThat(RequestRouteCategory.classify("/search", 200)).isEqualTo(RequestRouteCategory.PUBLIC_SEARCH);
        assertThat(RequestRouteCategory.classify("/contact", 200)).isEqualTo(RequestRouteCategory.PUBLIC_CONTACT);
        assertThat(RequestRouteCategory.classify("/cv/download", 200)).isEqualTo(RequestRouteCategory.PUBLIC_CV);
        assertThat(RequestRouteCategory.classify("/en/articles/hello", 200))
                .isEqualTo(RequestRouteCategory.PUBLIC_CONTENT);
        assertThat(RequestRouteCategory.classify("/tr/projects", 200))
                .isEqualTo(RequestRouteCategory.PUBLIC_PROJECT);
        assertThat(RequestRouteCategory.classify("/admin/contact/123", 200))
                .isEqualTo(RequestRouteCategory.ADMIN);
        assertThat(RequestRouteCategory.classify("/assets/main.css", 200))
                .isEqualTo(RequestRouteCategory.STATIC_ASSET);
        assertThat(RequestRouteCategory.classify("/media/assets/abc/variants/large", 200))
                .isEqualTo(RequestRouteCategory.MEDIA_VARIANT);
        assertThat(RequestRouteCategory.classify("/sitemap.xml", 200)).isEqualTo(RequestRouteCategory.SITEMAP);
        assertThat(RequestRouteCategory.classify("/robots.txt", 200)).isEqualTo(RequestRouteCategory.ROBOTS);
        assertThat(RequestRouteCategory.classify("/feed.xml", 200)).isEqualTo(RequestRouteCategory.FEED);
        assertThat(RequestRouteCategory.classify("/oauth2/authorization/owner", 302))
                .isEqualTo(RequestRouteCategory.OAUTH);
        assertThat(RequestRouteCategory.classify("/actuator/health", 200))
                .isEqualTo(RequestRouteCategory.ACTUATOR);
    }

    @Test
    void mapsUnknownNotFoundRequestsToBoundedNotFoundCategory() {
        assertThat(RequestRouteCategory.classify("/en/articles/missing-secret-slug", 404))
                .isEqualTo(RequestRouteCategory.PUBLIC_CONTENT);
        assertThat(RequestRouteCategory.classify("/totally-unknown-secret-path", 404))
                .isEqualTo(RequestRouteCategory.PUBLIC_NOT_FOUND);
    }

    @Test
    void mapsUnmatchedRequestsToOther() {
        assertThat(RequestRouteCategory.classify("/totally-unknown-secret-path", 200))
                .isEqualTo(RequestRouteCategory.OTHER);
    }

    @Test
    void neverExposesRawPathThroughCategoryName() {
        String sensitivePath = "/en/articles/leaked-secret-slug?token=abc";
        RequestRouteCategory category = RequestRouteCategory.classify(sensitivePath, 200);

        assertThat(category.name()).doesNotContain("leaked-secret-slug");
        assertThat(category.name()).doesNotContain("token");
    }
}
