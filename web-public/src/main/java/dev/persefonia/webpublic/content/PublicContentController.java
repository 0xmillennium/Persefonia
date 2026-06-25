package dev.persefonia.webpublic.content;

import dev.persefonia.contentpublishing.application.query.PublicContentLookupResult;
import dev.persefonia.contentpublishing.application.service.PublicContentBySourceQueryHandler;
import dev.persefonia.contentpublishing.application.service.PublicTranslationLinkQueryService;
import dev.persefonia.webpublic.insights.PublicInsightSurface;
import dev.persefonia.webpublic.insights.PublicInsightsObservationGateway;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.ModelAndView;

@Controller
public final class PublicContentController {
    private final PublicContentRouteParser routeParser;
    private final DiscoveryPublicContentRouteResolver routeResolver;
    private final PublicContentBySourceQueryHandler queryHandler;
    private final PublicTranslationLinkQueryService translationLinkQueryService;
    private final PublicContentViewModelFactory viewModelFactory;
    private final PublicContentResponseHeaders responseHeaders;
    private final PublicInsightsObservationGateway insights;

    public PublicContentController(
            PublicContentRouteParser routeParser,
            DiscoveryPublicContentRouteResolver routeResolver,
            PublicContentBySourceQueryHandler queryHandler,
            PublicTranslationLinkQueryService translationLinkQueryService,
            PublicContentViewModelFactory viewModelFactory,
            PublicContentResponseHeaders responseHeaders,
            PublicInsightsObservationGateway insights) {
        this.routeParser = routeParser;
        this.routeResolver = routeResolver;
        this.queryHandler = queryHandler;
        this.translationLinkQueryService = translationLinkQueryService;
        this.viewModelFactory = viewModelFactory;
        this.responseHeaders = responseHeaders;
        this.insights = insights;
    }

    @GetMapping("/{language}/{collection}/{slug}")
    public ModelAndView content(
            @PathVariable("language") String language,
            @PathVariable("collection") String collection,
            @PathVariable("slug") String slug,
            HttpServletResponse response) {
        return routeParser.parse(language, collection, slug)
                .map(routeResolver::resolve)
                .map(outcome -> handle(outcome, response))
                .orElseGet(() -> notFound(response));
    }

    private ModelAndView handle(DiscoveryPublicRouteOutcome outcome, HttpServletResponse response) {
        return switch (outcome) {
            case DiscoveryPublicRouteOutcome.Redirect redirect -> redirect(redirect, response);
            case DiscoveryPublicRouteOutcome.Content content -> render(queryHandler.lookup(content.query()), response);
            case DiscoveryPublicRouteOutcome.NotFound ignored -> notFound(response);
        };
    }

    private ModelAndView redirect(DiscoveryPublicRouteOutcome.Redirect redirect, HttpServletResponse response) {
        responseHeaders.applyPublicRedirectHeaders(response);
        response.setHeader("Location", redirect.targetPath());
        ModelAndView modelAndView = new ModelAndView();
        modelAndView.setStatus(HttpStatus.valueOf(redirect.statusCode()));
        return modelAndView;
    }

    private ModelAndView render(PublicContentLookupResult result, HttpServletResponse response) {
        if (result instanceof PublicContentLookupResult.Found found) {
            responseHeaders.applyPublicContentHeaders(response);
            insights.recordPageView(PublicInsightSurface.CONTENT_DETAIL);
            return new ModelAndView(
                    "site/content",
                    "page",
                    viewModelFactory.contentPage(found.page(), translationLinkQueryService.linksFor(found.page())));
        }
        return notFound(response);
    }

    private ModelAndView notFound(HttpServletResponse response) {
        responseHeaders.applyPublicNotFoundHeaders(response);
        insights.recordNotFound();
        ModelAndView modelAndView = new ModelAndView("site/not-found", "page", viewModelFactory.notFoundPage());
        modelAndView.setStatus(HttpStatus.NOT_FOUND);
        return modelAndView;
    }
}
