package dev.persefonia.webpublic.content;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.contentpublishing.application.query.PublicContentRouteQuery;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.content.Slug;
import dev.persefonia.discovery.application.contract.CanonicalUrl;
import dev.persefonia.discovery.application.contract.DiscoverableResourceType;
import dev.persefonia.discovery.application.contract.DiscoveryLanguage;
import dev.persefonia.discovery.application.contract.IndexingPolicy;
import dev.persefonia.discovery.application.contract.PublicUrl;
import dev.persefonia.discovery.application.contract.RedirectStatusCode;
import dev.persefonia.discovery.application.contract.RoutePurpose;
import dev.persefonia.discovery.application.contract.SourceContext;
import dev.persefonia.discovery.application.contract.SourceEntityId;
import dev.persefonia.discovery.application.contract.SourceType;
import dev.persefonia.discovery.application.port.ResolvePublicRoutePort;
import dev.persefonia.discovery.application.route.PublicRouteLookup;
import dev.persefonia.discovery.application.route.PublicRouteResolution;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DiscoveryPublicContentRouteResolverTest {
    @Test
    void buildsPathOnlyLookupFromValidatedRouteAndMapsFoundToSourceQuery() {
        UUID contentId = UUID.randomUUID();
        RecordingRoutePort port = new RecordingRoutePort(found(contentId, "/tr/articles/current-route"));
        DiscoveryPublicContentRouteResolver resolver = new DiscoveryPublicContentRouteResolver(port);

        DiscoveryPublicRouteOutcome outcome = resolver.resolve(
                new PublicContentRouteQuery(ContentType.ARTICLE, ContentLanguage.TR, Slug.of("current-route")));

        assertThat(port.lookupPath).isEqualTo("/tr/articles/current-route");
        assertThat(outcome).isInstanceOfSatisfying(DiscoveryPublicRouteOutcome.Content.class, content -> {
            assertThat(content.query().contentItemId()).isEqualTo(contentId);
            assertThat(content.query().expectedPublicPath()).isEqualTo("/tr/articles/current-route");
        });
    }

    @Test
    void mapsRedirectStatusAndPathOnlyLocationWithoutContentLookup() {
        RecordingRoutePort port = new RecordingRoutePort(new PublicRouteResolution.Redirect(
                RedirectStatusCode.TEMPORARY_REDIRECT_307,
                new PublicUrl("/tr/articles/new-route")));
        DiscoveryPublicContentRouteResolver resolver = new DiscoveryPublicContentRouteResolver(port);

        DiscoveryPublicRouteOutcome outcome = resolver.resolve(
                new PublicContentRouteQuery(ContentType.ARTICLE, ContentLanguage.TR, Slug.of("old-route")));

        assertThat(outcome).isInstanceOfSatisfying(DiscoveryPublicRouteOutcome.Redirect.class, redirect -> {
            assertThat(redirect.statusCode()).isEqualTo(307);
            assertThat(redirect.targetPath()).isEqualTo("/tr/articles/new-route");
        });
    }

    @Test
    void mapsMissingRouteToNotFound() {
        DiscoveryPublicContentRouteResolver resolver =
                new DiscoveryPublicContentRouteResolver(new RecordingRoutePort(new PublicRouteResolution.NotFound()));

        DiscoveryPublicRouteOutcome outcome = resolver.resolve(
                new PublicContentRouteQuery(ContentType.ARTICLE, ContentLanguage.TR, Slug.of("missing")));

        assertThat(outcome).isInstanceOf(DiscoveryPublicRouteOutcome.NotFound.class);
    }

    private static PublicRouteResolution.Found found(UUID contentId, String path) {
        return new PublicRouteResolution.Found(
                SourceContext.CONTENT_PUBLISHING,
                SourceType.CONTENT_ITEM,
                new SourceEntityId(contentId),
                DiscoverableResourceType.ARTICLE,
                RoutePurpose.DETAIL,
                DiscoveryLanguage.TR,
                new PublicUrl(path),
                new CanonicalUrl("https://0xmillennium.dev" + path),
                IndexingPolicy.INDEX);
    }

    private static final class RecordingRoutePort implements ResolvePublicRoutePort {
        private final PublicRouteResolution resolution;
        private String lookupPath;

        private RecordingRoutePort(PublicRouteResolution resolution) {
            this.resolution = resolution;
        }

        @Override
        public PublicRouteResolution resolve(PublicRouteLookup lookup) {
            lookupPath = lookup.publicUrl().value();
            return resolution;
        }
    }
}
