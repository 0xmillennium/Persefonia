package dev.persefonia.app.webpublic.content;

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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class InMemoryPublicRouteResolver implements ResolvePublicRoutePort {
    private final Map<String, PublicRouteResolution.Found> foundRoutes = new LinkedHashMap<>();
    private final Map<String, PublicRouteResolution.Redirect> redirects = new LinkedHashMap<>();

    @Override
    public PublicRouteResolution resolve(PublicRouteLookup lookup) {
        String publicPath = lookup.publicUrl().value();
        PublicRouteResolution.Redirect redirect = redirects.get(publicPath);
        if (redirect != null) {
            return redirect;
        }
        PublicRouteResolution.Found found = foundRoutes.get(publicPath);
        if (found != null) {
            return found;
        }
        return new PublicRouteResolution.NotFound();
    }

    public void addFound(String publicPath, UUID sourceEntityId) {
        addFound(new PublicUrl(publicPath), sourceEntityId);
    }

    public void addFound(PublicUrl publicUrl, UUID sourceEntityId) {
        foundRoutes.put(publicUrl.value(), found(publicUrl, sourceEntityId));
    }

    public void addRedirect(String sourcePath, String targetPath, RedirectStatusCode statusCode) {
        redirects.put(sourcePath, new PublicRouteResolution.Redirect(statusCode, new PublicUrl(targetPath)));
    }

    public void clear() {
        foundRoutes.clear();
        redirects.clear();
    }

    private static PublicRouteResolution.Found found(PublicUrl publicUrl, UUID sourceEntityId) {
        return new PublicRouteResolution.Found(
                SourceContext.CONTENT_PUBLISHING,
                SourceType.CONTENT_ITEM,
                new SourceEntityId(sourceEntityId),
                DiscoverableResourceType.ARTICLE,
                RoutePurpose.DETAIL,
                DiscoveryLanguage.TR,
                publicUrl,
                new CanonicalUrl("https://0xmillennium.dev" + publicUrl.value()),
                IndexingPolicy.INDEX);
    }
}
