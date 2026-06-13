package dev.persefonia.webpublic.content;

import dev.persefonia.contentpublishing.application.query.PublicContentLookupResult;
import dev.persefonia.contentpublishing.application.service.PublicContentQueryHandler;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.ModelAndView;

@Controller
public final class PublicContentController {
    private final PublicContentRouteParser routeParser;
    private final PublicContentQueryHandler queryHandler;
    private final PublicContentViewModelFactory viewModelFactory;
    private final PublicContentResponseHeaders responseHeaders;

    public PublicContentController(
            PublicContentRouteParser routeParser,
            PublicContentQueryHandler queryHandler,
            PublicContentViewModelFactory viewModelFactory,
            PublicContentResponseHeaders responseHeaders) {
        this.routeParser = routeParser;
        this.queryHandler = queryHandler;
        this.viewModelFactory = viewModelFactory;
        this.responseHeaders = responseHeaders;
    }

    @GetMapping("/{language}/{collection}/{slug}")
    public ModelAndView content(
            @PathVariable("language") String language,
            @PathVariable("collection") String collection,
            @PathVariable("slug") String slug,
            HttpServletResponse response) {
        return routeParser.parse(language, collection, slug)
                .map(queryHandler::lookup)
                .map(result -> render(result, response))
                .orElseGet(() -> notFound(response));
    }

    private ModelAndView render(PublicContentLookupResult result, HttpServletResponse response) {
        if (result instanceof PublicContentLookupResult.Found found) {
            responseHeaders.applyPublicContentHeaders(response);
            return new ModelAndView("site/content", "page", viewModelFactory.contentPage(found.page()));
        }
        return notFound(response);
    }

    private ModelAndView notFound(HttpServletResponse response) {
        responseHeaders.applyPublicNotFoundHeaders(response);
        ModelAndView modelAndView = new ModelAndView("site/not-found", "page", viewModelFactory.notFoundPage());
        modelAndView.setStatus(HttpStatus.NOT_FOUND);
        return modelAndView;
    }
}
