package dev.persefonia.webpublic.content;

import dev.persefonia.contentpublishing.application.query.PublicContentLookupResult;
import dev.persefonia.contentpublishing.application.service.PublicContentQueryHandler;
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

    public PublicContentController(
            PublicContentRouteParser routeParser,
            PublicContentQueryHandler queryHandler,
            PublicContentViewModelFactory viewModelFactory) {
        this.routeParser = routeParser;
        this.queryHandler = queryHandler;
        this.viewModelFactory = viewModelFactory;
    }

    @GetMapping("/{language}/{collection}/{slug}")
    public ModelAndView content(
            @PathVariable("language") String language,
            @PathVariable("collection") String collection,
            @PathVariable("slug") String slug) {
        return routeParser.parse(language, collection, slug)
                .map(queryHandler::lookup)
                .map(this::render)
                .orElseGet(this::notFound);
    }

    private ModelAndView render(PublicContentLookupResult result) {
        if (result instanceof PublicContentLookupResult.Found found) {
            return new ModelAndView("site/content", "page", viewModelFactory.contentPage(found.page()));
        }
        return notFound();
    }

    private ModelAndView notFound() {
        ModelAndView modelAndView = new ModelAndView("site/not-found", "page", viewModelFactory.notFoundPage());
        modelAndView.setStatus(HttpStatus.NOT_FOUND);
        return modelAndView;
    }
}
