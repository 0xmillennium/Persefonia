package dev.persefonia.webpublic.search;

import dev.persefonia.webpublic.FrontendAssetResolver;
import dev.persefonia.webpublic.content.PublicCanonicalUrlFactory;
import dev.persefonia.webpublic.content.PublicContentResponseHeaders;
import dev.persefonia.webpublic.insights.PublicInsightSurface;
import dev.persefonia.webpublic.insights.PublicInsightsObservationGateway;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Objects;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

@Controller
public final class PublicSearchController {
    private static final String FRONTEND_ENTRY = "src/main.ts";

    private final PublicSearchPageService pages;
    private final FrontendAssetResolver assetResolver;
    private final PublicCanonicalUrlFactory canonicalUrlFactory;
    private final PublicContentResponseHeaders responseHeaders;
    private final PublicInsightsObservationGateway insights;

    public PublicSearchController(
            PublicSearchPageService pages,
            FrontendAssetResolver assetResolver,
            PublicCanonicalUrlFactory canonicalUrlFactory,
            PublicContentResponseHeaders responseHeaders,
            PublicInsightsObservationGateway insights) {
        this.pages = Objects.requireNonNull(pages, "pages");
        this.assetResolver = Objects.requireNonNull(assetResolver, "assetResolver");
        this.canonicalUrlFactory = Objects.requireNonNull(canonicalUrlFactory, "canonicalUrlFactory");
        this.responseHeaders = Objects.requireNonNull(responseHeaders, "responseHeaders");
        this.insights = Objects.requireNonNull(insights, "insights");
    }

    @GetMapping("/search")
    public ModelAndView search(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "page", required = false) String page,
            HttpServletResponse response) {
        responseHeaders.applyPublicSearchHeaders(response);
        PublicSearchPage searchPage = pages.page(
                query,
                page,
                canonicalUrlFactory.canonicalUrl("/search"),
                assetResolver.stylesheetPaths(FRONTEND_ENTRY));
        if (searchPage.hasSearched()) {
            insights.recordSearchSubmitted();
        } else if (query == null || query.isBlank()) {
            insights.recordPageView(PublicInsightSurface.SEARCH);
        }
        return new ModelAndView("site/search/index", "page", searchPage);
    }
}
