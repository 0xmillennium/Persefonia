package dev.persefonia.webpublic.content;

import dev.persefonia.contentpublishing.application.query.PublicContentBySourceQuery;
import dev.persefonia.contentpublishing.application.query.PublicContentRouteQuery;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentType;
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
public final class DiscoveryPublicContentRouteResolver {
    private final ResolvePublicRoutePort routeResolver;

    public DiscoveryPublicContentRouteResolver(ResolvePublicRoutePort routeResolver) {
        this.routeResolver = Objects.requireNonNull(routeResolver, "routeResolver");
    }

    public DiscoveryPublicRouteOutcome resolve(PublicContentRouteQuery route) {
        Objects.requireNonNull(route, "route");
        String expectedPublicPath = publicPath(route);
        PublicRouteResolution resolution = routeResolver.resolve(new PublicRouteLookup(new PublicUrl(expectedPublicPath)));

        return switch (resolution) {
            case PublicRouteResolution.Redirect redirect ->
                    new DiscoveryPublicRouteOutcome.Redirect(
                            redirect.statusCode().value(),
                            redirect.targetUrl().value());
            case PublicRouteResolution.Found found when isSupportedContentDetail(found) ->
                    new DiscoveryPublicRouteOutcome.Content(new PublicContentBySourceQuery(
                            found.sourceEntityId().value(),
                            expectedPublicPath));
            case PublicRouteResolution.Found ignored -> new DiscoveryPublicRouteOutcome.NotFound();
            case PublicRouteResolution.NotFound ignored -> new DiscoveryPublicRouteOutcome.NotFound();
        };
    }

    private static boolean isSupportedContentDetail(PublicRouteResolution.Found found) {
        return found.sourceContext() == SourceContext.CONTENT_PUBLISHING
                && found.sourceType() == SourceType.CONTENT_ITEM
                && found.routePurpose() == RoutePurpose.DETAIL;
    }

    private static String publicPath(PublicContentRouteQuery route) {
        return "/" + languageSegment(route.language()) + "/" + collectionSegment(route.type()) + "/" + route.slug().value();
    }

    private static String languageSegment(ContentLanguage language) {
        return switch (language) {
            case TR -> "tr";
            case EN -> "en";
        };
    }

    private static String collectionSegment(ContentType type) {
        return switch (type) {
            case ARTICLE -> "articles";
            case NOTE -> "notes";
            case RESEARCH -> "research";
            case PAGE -> "pages";
        };
    }
}
