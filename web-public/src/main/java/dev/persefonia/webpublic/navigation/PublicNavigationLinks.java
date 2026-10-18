package dev.persefonia.webpublic.navigation;

import dev.persefonia.contentpublishing.application.query.PublicContentLookupResult;
import dev.persefonia.contentpublishing.application.service.PublicContentBySourceQueryHandler;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import dev.persefonia.profileportfolio.application.publicview.PublicCvRoutes;
import dev.persefonia.profileportfolio.application.service.ActiveCvPublicQueryService;
import dev.persefonia.webpublic.content.DiscoveryPublicContentRouteResolver;
import dev.persefonia.webpublic.content.DiscoveryPublicRouteOutcome;
import dev.persefonia.webpublic.content.PublicContentRouteParser;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Resolves optional destinations through both the public route and source eligibility contracts. */
@Component
public final class PublicNavigationLinks {
    private final ObjectProvider<ActiveCvPublicQueryService> cvs;
    private final ObjectProvider<PublicContentBySourceQueryHandler> contents;
    private final ObjectProvider<DiscoveryPublicContentRouteResolver> routes;
    private final PublicContentRouteParser parser = new PublicContentRouteParser();

    public PublicNavigationLinks(ObjectProvider<ActiveCvPublicQueryService> cvs,
            ObjectProvider<PublicContentBySourceQueryHandler> contents,
            ObjectProvider<DiscoveryPublicContentRouteResolver> routes) {
        this.cvs = cvs;
        this.contents = contents;
        this.routes = routes;
    }

    public List<PublicNavigationLink> forLanguage(String language) {
        var links = new ArrayList<PublicNavigationLink>();
        var cv = cvs.getIfAvailable();
        if (cv != null) {
            cv.explicitLanguageView(language).ifPresent(view -> links.add(new PublicNavigationLink(
                    "CV", PublicCvRoutes.LANGUAGE_PAGE.replace("{language}", view.language()))));
        }
        var source = contents.getIfAvailable();
        var resolver = routes.getIfAvailable();
        if (source != null && resolver != null) {
            // Architecture is an ordinary PAGE with this editorial slug, never a reserved or fabricated route.
            parser.parse(language, "pages", "architecture").map(resolver::resolve)
                    .filter(DiscoveryPublicRouteOutcome.Content.class::isInstance)
                    .map(DiscoveryPublicRouteOutcome.Content.class::cast)
                    .map(route -> source.lookup(route.query()))
                    .filter(PublicContentLookupResult.Found.class::isInstance)
                    .map(PublicContentLookupResult.Found.class::cast)
                    .filter(found -> found.page().visibility() == ContentVisibility.PUBLIC)
                    .ifPresent(found -> links.add(new PublicNavigationLink(
                            "Architecture", found.page().canonicalPath().value())));
        }
        return List.copyOf(links);
    }
}
