package dev.persefonia.webpublic.series;

import dev.persefonia.contentpublishing.application.query.PublicSeriesLookupResult;
import dev.persefonia.contentpublishing.application.service.PublicSeriesPageQueryService;
import dev.persefonia.webpublic.content.PublicContentResponseHeaders;
import dev.persefonia.webpublic.content.PublicContentViewModelFactory;
import dev.persefonia.webpublic.insights.PublicInsightSurface;
import dev.persefonia.webpublic.insights.PublicInsightsObservationGateway;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.ModelAndView;

@Controller
public final class PublicSeriesPageController {
    private final PublicSeriesRouteParser routeParser;
    private final DiscoveryPublicSeriesRouteResolver routeResolver;
    private final PublicSeriesPageQueryService queryService;
    private final PublicSeriesPageViewModelFactory viewModelFactory;
    private final PublicContentViewModelFactory publicContentViewModelFactory;
    private final PublicContentResponseHeaders responseHeaders;
    private final PublicInsightsObservationGateway insights;

    public PublicSeriesPageController(
            PublicSeriesRouteParser routeParser,
            DiscoveryPublicSeriesRouteResolver routeResolver,
            PublicSeriesPageQueryService queryService,
            PublicSeriesPageViewModelFactory viewModelFactory,
            PublicContentViewModelFactory publicContentViewModelFactory,
            PublicContentResponseHeaders responseHeaders,
            PublicInsightsObservationGateway insights) {
        this.routeParser = routeParser;
        this.routeResolver = routeResolver;
        this.queryService = queryService;
        this.viewModelFactory = viewModelFactory;
        this.publicContentViewModelFactory = publicContentViewModelFactory;
        this.responseHeaders = responseHeaders;
        this.insights = insights;
    }

    @GetMapping("/{language}/series/{seriesSlug}")
    public ModelAndView series(
            @PathVariable("language") String language,
            @PathVariable("seriesSlug") String seriesSlug,
            HttpServletResponse response) {
        return routeParser.parse(language, seriesSlug)
                .map(routeResolver::resolve)
                .map(outcome -> render(outcome, response))
                .orElseGet(() -> notFound(response));
    }

    private ModelAndView render(DiscoveryPublicSeriesRouteOutcome outcome, HttpServletResponse response) {
        if (!(outcome instanceof DiscoveryPublicSeriesRouteOutcome.Series seriesRoute)) {
            return notFound(response);
        }
        PublicSeriesLookupResult result = queryService.lookup(seriesRoute.query());
        if (!(result instanceof PublicSeriesLookupResult.Found found)) {
            return notFound(response);
        }
        responseHeaders.applyPublicContentHeaders(response);
        insights.recordPageView(PublicInsightSurface.SERIES_INDEX);
        return new ModelAndView(
                "site/series",
                "page",
                viewModelFactory.page(
                        found.page(),
                        seriesRoute.language(),
                        seriesRoute.publicUrl(),
                        seriesRoute.canonicalUrl()));
    }

    private ModelAndView notFound(HttpServletResponse response) {
        responseHeaders.applyPublicNotFoundHeaders(response);
        insights.recordNotFound();
        ModelAndView modelAndView =
                new ModelAndView("site/not-found", "page", publicContentViewModelFactory.notFoundPage());
        modelAndView.setStatus(HttpStatus.NOT_FOUND);
        return modelAndView;
    }
}
