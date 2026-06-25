package dev.persefonia.webpublic.projects;

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
public final class DiscoveryPublicProjectRouteResolver {
    private final ResolvePublicRoutePort routeResolver;

    public DiscoveryPublicProjectRouteResolver(ResolvePublicRoutePort routeResolver) {
        this.routeResolver = Objects.requireNonNull(routeResolver, "routeResolver");
    }

    public DiscoveryPublicProjectRouteOutcome resolve(PublicProjectRoute route) {
        Objects.requireNonNull(route, "route");
        PublicRouteResolution resolution =
                routeResolver.resolve(new PublicRouteLookup(new PublicUrl(route.publicPath())));
        return switch (resolution) {
            case PublicRouteResolution.Redirect redirect ->
                    new DiscoveryPublicProjectRouteOutcome.Redirect(
                            redirect.statusCode().value(),
                            redirect.targetUrl().value());
            case PublicRouteResolution.Found found when isSupportedProjectDetail(found, route) ->
                    new DiscoveryPublicProjectRouteOutcome.Project(
                            found.sourceEntityId().value(),
                            route.language().name(),
                            route.slug(),
                            found.publicUrl().value(),
                            found.canonicalUrl().value(),
                            found.indexingPolicy() == IndexingPolicy.NO_INDEX);
            case PublicRouteResolution.Found ignored -> new DiscoveryPublicProjectRouteOutcome.NotFound();
            case PublicRouteResolution.NotFound ignored -> new DiscoveryPublicProjectRouteOutcome.NotFound();
        };
    }

    private static boolean isSupportedProjectDetail(PublicRouteResolution.Found found, PublicProjectRoute route) {
        return found.sourceContext() == SourceContext.PROFILE_PORTFOLIO
                && found.sourceType() == SourceType.PROJECT
                && found.resourceType() == DiscoverableResourceType.PROJECT
                && found.routePurpose() == RoutePurpose.DETAIL
                && found.language() == route.language()
                && isSupportedIndexingPolicy(found.indexingPolicy())
                && found.publicUrl().value().equals(route.publicPath());
    }

    private static boolean isSupportedIndexingPolicy(IndexingPolicy indexingPolicy) {
        return indexingPolicy == IndexingPolicy.INDEX || indexingPolicy == IndexingPolicy.NO_INDEX;
    }
}
