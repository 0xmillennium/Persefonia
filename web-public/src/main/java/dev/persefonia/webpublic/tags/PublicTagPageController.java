package dev.persefonia.webpublic.tags;

import dev.persefonia.contentpublishing.application.query.PublicTaggedContentQuery;
import dev.persefonia.contentpublishing.application.service.PublicTaggedContentQueryHandler;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.TagId;
import dev.persefonia.taxonomy.application.query.PublicTagLookupResult;
import dev.persefonia.taxonomy.application.service.PublicTagBySourceQueryHandler;
import dev.persefonia.webpublic.content.PublicContentResponseHeaders;
import dev.persefonia.webpublic.content.PublicContentViewModelFactory;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.ModelAndView;

@Controller
public final class PublicTagPageController {
    private static final int CONTENT_LIMIT = 50;

    private final PublicTagRouteParser routeParser;
    private final DiscoveryPublicTagRouteResolver routeResolver;
    private final PublicTagBySourceQueryHandler tagQueryHandler;
    private final PublicTaggedContentQueryHandler contentQueryHandler;
    private final PublicTagPageViewModelFactory viewModelFactory;
    private final PublicContentViewModelFactory publicContentViewModelFactory;
    private final PublicContentResponseHeaders responseHeaders;

    public PublicTagPageController(
            PublicTagRouteParser routeParser,
            DiscoveryPublicTagRouteResolver routeResolver,
            PublicTagBySourceQueryHandler tagQueryHandler,
            PublicTaggedContentQueryHandler contentQueryHandler,
            PublicTagPageViewModelFactory viewModelFactory,
            PublicContentViewModelFactory publicContentViewModelFactory,
            PublicContentResponseHeaders responseHeaders) {
        this.routeParser = routeParser;
        this.routeResolver = routeResolver;
        this.tagQueryHandler = tagQueryHandler;
        this.contentQueryHandler = contentQueryHandler;
        this.viewModelFactory = viewModelFactory;
        this.publicContentViewModelFactory = publicContentViewModelFactory;
        this.responseHeaders = responseHeaders;
    }

    @GetMapping("/{language}/tags/{tagSlug}")
    public ModelAndView tag(
            @PathVariable("language") String language,
            @PathVariable("tagSlug") String tagSlug,
            HttpServletResponse response) {
        return routeParser.parse(language, tagSlug)
                .map(routeResolver::resolve)
                .map(outcome -> render(outcome, response))
                .orElseGet(() -> notFound(response));
    }

    private ModelAndView render(DiscoveryPublicTagRouteOutcome outcome, HttpServletResponse response) {
        if (!(outcome instanceof DiscoveryPublicTagRouteOutcome.Tag tagRoute)) {
            return notFound(response);
        }
        PublicTagLookupResult tagResult = tagQueryHandler.lookup(tagRoute.query());
        if (!(tagResult instanceof PublicTagLookupResult.Found found)) {
            return notFound(response);
        }
        var items = contentQueryHandler.list(new PublicTaggedContentQuery(
                TagId.from(found.tag().tagId()),
                ContentLanguage.valueOf(tagRoute.language().name()),
                CONTENT_LIMIT));
        responseHeaders.applyPublicContentHeaders(response);
        return new ModelAndView(
                "site/tag",
                "page",
                viewModelFactory.page(
                        found.tag(),
                        tagRoute.language(),
                        tagRoute.publicUrl(),
                        tagRoute.canonicalUrl(),
                        items));
    }

    private ModelAndView notFound(HttpServletResponse response) {
        responseHeaders.applyPublicNotFoundHeaders(response);
        ModelAndView modelAndView =
                new ModelAndView("site/not-found", "page", publicContentViewModelFactory.notFoundPage());
        modelAndView.setStatus(HttpStatus.NOT_FOUND);
        return modelAndView;
    }
}
