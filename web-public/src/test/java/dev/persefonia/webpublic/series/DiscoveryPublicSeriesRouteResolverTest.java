package dev.persefonia.webpublic.series;

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
import dev.persefonia.discovery.application.route.PublicRouteLookup;
import dev.persefonia.discovery.application.route.PublicRouteResolution;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DiscoveryPublicSeriesRouteResolverTest {
    @Test
    void resolvesSupportedSeriesPageThroughDiscovery() {
        UUID seriesId = UUID.randomUUID();
        DiscoveryPublicSeriesRouteResolver resolver = new DiscoveryPublicSeriesRouteResolver(lookup -> found(
                lookup.publicUrl(),
                seriesId,
                SourceContext.CONTENT_PUBLISHING,
                SourceType.SERIES,
                DiscoverableResourceType.SERIES,
                RoutePurpose.SERIES_PAGE,
                DiscoveryLanguage.EN,
                IndexingPolicy.NO_INDEX));

        DiscoveryPublicSeriesRouteOutcome outcome =
                resolver.resolve(new PublicSeriesRoute(DiscoveryLanguage.EN, "spring-boot-notes"));

        assertThat(outcome).isInstanceOfSatisfying(DiscoveryPublicSeriesRouteOutcome.Series.class, series -> {
            assertThat(series.query().seriesId()).isEqualTo(seriesId);
            assertThat(series.query().expectedSlug()).isEqualTo("spring-boot-notes");
            assertThat(series.publicUrl()).isEqualTo("/en/series/spring-boot-notes");
        });
    }

    @Test
    void rejectsUnsupportedOrStaleDiscoveryProjection() {
        PublicSeriesRoute route = new PublicSeriesRoute(DiscoveryLanguage.EN, "spring-boot-notes");

        assertThat(resolve(route, found(
                        new PublicUrl(route.publicPath()),
                        UUID.randomUUID(),
                        SourceContext.CONTENT_PUBLISHING,
                        SourceType.CONTENT_ITEM,
                        DiscoverableResourceType.ARTICLE,
                        RoutePurpose.DETAIL,
                        DiscoveryLanguage.EN,
                        IndexingPolicy.NO_INDEX)))
                .isInstanceOf(DiscoveryPublicSeriesRouteOutcome.NotFound.class);
        assertThat(resolve(route, found(
                        new PublicUrl("/en/series/old"),
                        UUID.randomUUID(),
                        SourceContext.CONTENT_PUBLISHING,
                        SourceType.SERIES,
                        DiscoverableResourceType.SERIES,
                        RoutePurpose.SERIES_PAGE,
                        DiscoveryLanguage.EN,
                        IndexingPolicy.NO_INDEX)))
                .isInstanceOf(DiscoveryPublicSeriesRouteOutcome.NotFound.class);
    }

    private static DiscoveryPublicSeriesRouteOutcome resolve(
            PublicSeriesRoute route,
            PublicRouteResolution resolution) {
        return new DiscoveryPublicSeriesRouteResolver(new StaticRoutePort(resolution)).resolve(route);
    }

    private static PublicRouteResolution.Found found(
            PublicUrl publicUrl,
            UUID sourceEntityId,
            SourceContext sourceContext,
            SourceType sourceType,
            DiscoverableResourceType resourceType,
            RoutePurpose routePurpose,
            DiscoveryLanguage language,
            IndexingPolicy indexingPolicy) {
        return new PublicRouteResolution.Found(
                sourceContext,
                sourceType,
                new SourceEntityId(sourceEntityId),
                resourceType,
                routePurpose,
                language,
                publicUrl,
                new CanonicalUrl("https://0xmillennium.dev" + publicUrl.value()),
                indexingPolicy);
    }

    private record StaticRoutePort(PublicRouteResolution resolution) implements ResolvePublicRoutePort {
        @Override
        public PublicRouteResolution resolve(PublicRouteLookup lookup) {
            return resolution;
        }
    }
}
