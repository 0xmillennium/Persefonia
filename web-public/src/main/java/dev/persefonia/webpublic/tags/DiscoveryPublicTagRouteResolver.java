package dev.persefonia.webpublic.tags;

import dev.persefonia.discovery.application.contract.DiscoverableResourceType;
import dev.persefonia.discovery.application.contract.IndexingPolicy;
import dev.persefonia.discovery.application.contract.PublicUrl;
import dev.persefonia.discovery.application.contract.RoutePurpose;
import dev.persefonia.discovery.application.contract.SourceContext;
import dev.persefonia.discovery.application.contract.SourceType;
import dev.persefonia.discovery.application.port.ResolvePublicRoutePort;
import dev.persefonia.discovery.application.route.PublicRouteLookup;
import dev.persefonia.discovery.application.route.PublicRouteResolution;
import dev.persefonia.taxonomy.application.query.PublicTagBySourceQuery;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class DiscoveryPublicTagRouteResolver {
    private final ResolvePublicRoutePort routeResolver;

    public DiscoveryPublicTagRouteResolver(ResolvePublicRoutePort routeResolver) {
        this.routeResolver = Objects.requireNonNull(routeResolver, "routeResolver");
    }

    public DiscoveryPublicTagRouteOutcome resolve(PublicTagRoute route) {
        Objects.requireNonNull(route, "route");
        PublicRouteResolution resolution =
                routeResolver.resolve(new PublicRouteLookup(new PublicUrl(route.publicPath())));
        if (resolution instanceof PublicRouteResolution.Found found && isSupportedTagPage(found, route)) {
            return new DiscoveryPublicTagRouteOutcome.Tag(
                    new PublicTagBySourceQuery(found.sourceEntityId().value(), route.slug()),
                    found.language(),
                    found.publicUrl().value(),
                    found.canonicalUrl().value());
        }
        return new DiscoveryPublicTagRouteOutcome.NotFound();
    }

    private static boolean isSupportedTagPage(PublicRouteResolution.Found found, PublicTagRoute route) {
        return found.sourceContext() == SourceContext.TAXONOMY
                && found.sourceType() == SourceType.TAG
                && found.resourceType() == DiscoverableResourceType.TAG
                && found.routePurpose() == RoutePurpose.TAG_PAGE
                && found.language() == route.language()
                && found.indexingPolicy() == IndexingPolicy.NO_INDEX
                && found.publicUrl().value().equals(route.publicPath());
    }
}
