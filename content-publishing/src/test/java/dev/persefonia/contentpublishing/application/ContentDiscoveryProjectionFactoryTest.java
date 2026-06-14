package dev.persefonia.contentpublishing.application;

import static dev.persefonia.contentpublishing.application.support.ContentApplicationFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.contentpublishing.application.discovery.ConfiguredContentCanonicalUrlFactory;
import dev.persefonia.contentpublishing.application.discovery.ContentDiscoveryProjectionFactory;
import dev.persefonia.contentpublishing.application.discovery.ContentPublicRouteFactory;
import dev.persefonia.contentpublishing.domain.content.CanonicalPath;
import dev.persefonia.contentpublishing.domain.content.ContentMetadata;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import dev.persefonia.contentpublishing.domain.content.Slug;
import dev.persefonia.contentpublishing.domain.support.ContentItemTestFixtures;
import org.junit.jupiter.api.Test;

class ContentDiscoveryProjectionFactoryTest {
    private final ContentDiscoveryProjectionFactory factory = new ContentDiscoveryProjectionFactory(
            new ContentPublicRouteFactory(),
            new ConfiguredContentCanonicalUrlFactory("https://configured.example/"));

    @Test
    void slugRouteUpdateChangesPublicUrlAndCanonicalUrlTogether() {
        var item = ContentItemTestFixtures.published(ContentVisibility.PUBLIC);
        item.changeSlug(Slug.of("updated-route"), NOW);
        item.changeMetadata(ContentMetadata.withCanonicalPath(CanonicalPath.of("/articles/content-baseline")), NOW);

        var projection = factory.projectionFor(item).orElseThrow();

        assertThat(projection.publicUrl().value()).isEqualTo("/en/articles/updated-route");
        assertThat(projection.canonicalUrl().value()).isEqualTo("https://configured.example/en/articles/updated-route");
    }

    @Test
    void canonicalUrlUsesConfiguredBaseUrlAndCurrentPublicUrl() {
        var item = ContentItemTestFixtures.published(ContentVisibility.PUBLIC);

        var projection = factory.projectionFor(item).orElseThrow();

        assertThat(projection.publicUrl().value()).isEqualTo("/en/articles/content-baseline");
        assertThat(projection.canonicalUrl().value()).isEqualTo("https://configured.example/en/articles/content-baseline");
    }
}
