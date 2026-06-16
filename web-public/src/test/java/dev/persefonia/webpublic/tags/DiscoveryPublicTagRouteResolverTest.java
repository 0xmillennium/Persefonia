package dev.persefonia.webpublic.tags;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.discovery.application.contract.CanonicalUrl;
import dev.persefonia.discovery.application.contract.DiscoverableResourceType;
import dev.persefonia.discovery.application.contract.DiscoveryLanguage;
import dev.persefonia.discovery.application.contract.IndexingPolicy;
import dev.persefonia.discovery.application.contract.PublicUrl;
import dev.persefonia.discovery.application.contract.RoutePurpose;
import dev.persefonia.discovery.application.contract.SourceContext;
import dev.persefonia.discovery.application.contract.SourceEntityId;
import dev.persefonia.discovery.application.contract.SourceType;
import dev.persefonia.discovery.application.port.ResolvePublicRoutePort;
import dev.persefonia.discovery.application.route.PublicRouteResolution;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DiscoveryPublicTagRouteResolverTest {
    @Test
    void resolvesOnlyMatchingNoindexTagPageProjection() {
        UUID tagId = UUID.randomUUID();
        var resolver = new DiscoveryPublicTagRouteResolver(port(found(tagId, "/en/tags/spring", IndexingPolicy.NO_INDEX)));

        var outcome = resolver.resolve(new PublicTagRoute(DiscoveryLanguage.EN, "spring"));

        assertThat(outcome).isInstanceOfSatisfying(DiscoveryPublicTagRouteOutcome.Tag.class, tag -> {
            assertThat(tag.query().tagId()).isEqualTo(tagId);
            assertThat(tag.query().expectedSlug()).isEqualTo("spring");
            assertThat(tag.canonicalUrl()).isEqualTo("https://example.test/en/tags/spring");
        });
    }

    @Test
    void rejectsMissingStaleAndIndexableProjection() {
        assertThat(new DiscoveryPublicTagRouteResolver(port(new PublicRouteResolution.NotFound()))
                .resolve(new PublicTagRoute(DiscoveryLanguage.EN, "spring")))
                .isInstanceOf(DiscoveryPublicTagRouteOutcome.NotFound.class);
        assertThat(new DiscoveryPublicTagRouteResolver(port(found(UUID.randomUUID(), "/en/tags/old", IndexingPolicy.NO_INDEX)))
                .resolve(new PublicTagRoute(DiscoveryLanguage.EN, "spring")))
                .isInstanceOf(DiscoveryPublicTagRouteOutcome.NotFound.class);
        assertThat(new DiscoveryPublicTagRouteResolver(port(found(UUID.randomUUID(), "/en/tags/spring", IndexingPolicy.INDEX)))
                .resolve(new PublicTagRoute(DiscoveryLanguage.EN, "spring")))
                .isInstanceOf(DiscoveryPublicTagRouteOutcome.NotFound.class);
    }

    private static ResolvePublicRoutePort port(PublicRouteResolution resolution) {
        return lookup -> resolution;
    }

    private static PublicRouteResolution.Found found(UUID tagId, String path, IndexingPolicy indexingPolicy) {
        return new PublicRouteResolution.Found(
                SourceContext.TAXONOMY,
                SourceType.TAG,
                new SourceEntityId(tagId),
                DiscoverableResourceType.TAG,
                RoutePurpose.TAG_PAGE,
                DiscoveryLanguage.EN,
                new PublicUrl(path),
                new CanonicalUrl("https://example.test" + path),
                indexingPolicy);
    }
}
