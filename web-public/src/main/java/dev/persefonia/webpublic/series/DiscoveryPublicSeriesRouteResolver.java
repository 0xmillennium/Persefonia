package dev.persefonia.webpublic.series;

import dev.persefonia.contentpublishing.application.query.PublicSeriesBySourceQuery;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.discovery.application.contract.DiscoverableResourceType;
import dev.persefonia.discovery.application.contract.IndexingPolicy;
import dev.persefonia.discovery.application.contract.PublicUrl;
import dev.persefonia.discovery.application.contract.RoutePurpose;
import dev.persefonia.discovery.application.contract.SourceContext;
import dev.persefonia.discovery.application.contract.SourceType;
import dev.persefonia.discovery.application.port.ResolvePublicRoutePort;
import dev.persefonia.discovery.application.route.PublicRouteLookup;
import dev.persefonia.discovery.application.route.PublicRouteResolution;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class DiscoveryPublicSeriesRouteResolver {
    private final ResolvePublicRoutePort routeResolver;

    public DiscoveryPublicSeriesRouteResolver(ResolvePublicRoutePort routeResolver) {
        this.routeResolver = Objects.requireNonNull(routeResolver, "routeResolver");
    }

    public DiscoveryPublicSeriesRouteOutcome resolve(PublicSeriesRoute route) {
        Objects.requireNonNull(route, "route");
        PublicRouteResolution resolution =
                routeResolver.resolve(new PublicRouteLookup(new PublicUrl(route.publicPath())));
        if (resolution instanceof PublicRouteResolution.Found found && isSupportedSeriesPage(found, route)) {
            return new DiscoveryPublicSeriesRouteOutcome.Series(
                    new PublicSeriesBySourceQuery(
                            found.sourceEntityId().value(),
                            ContentLanguage.valueOf(route.language().name()),
                            route.slug()),
                    found.language(),
                    found.publicUrl().value(),
                    found.canonicalUrl().value());
        }
        return new DiscoveryPublicSeriesRouteOutcome.NotFound();
    }

    private static boolean isSupportedSeriesPage(PublicRouteResolution.Found found, PublicSeriesRoute route) {
        return found.sourceContext() == SourceContext.CONTENT_PUBLISHING
                && found.sourceType() == SourceType.SERIES
                && found.resourceType() == DiscoverableResourceType.SERIES
                && found.routePurpose() == RoutePurpose.SERIES_PAGE
                && found.language() == route.language()
                && found.indexingPolicy() == IndexingPolicy.NO_INDEX
                && found.publicUrl().value().equals(route.publicPath());
    }
}
