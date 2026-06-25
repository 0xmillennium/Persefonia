package dev.persefonia.webpublic.projects;

import dev.persefonia.profileportfolio.application.query.PublicProjectDetailView;
import dev.persefonia.profileportfolio.application.service.PublicProjectDetailQueryService;
import dev.persefonia.webpublic.FrontendAssetResolver;
import dev.persefonia.webpublic.content.PublicContentResponseHeaders;
import dev.persefonia.webpublic.content.PublicContentViewModelFactory;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.ModelAndView;

@Controller
public final class PublicProjectDetailController {
    private static final String MAIN_FRONTEND_ENTRY = "src/main.ts";

    private final PublicProjectRouteParser routeParser;
    private final DiscoveryPublicProjectRouteResolver routeResolver;
    private final PublicProjectDetailQueryService projects;
    private final FrontendAssetResolver assetResolver;
    private final PublicContentResponseHeaders responseHeaders;
    private final PublicContentViewModelFactory contentViewModelFactory;

    public PublicProjectDetailController(
            PublicProjectRouteParser routeParser,
            DiscoveryPublicProjectRouteResolver routeResolver,
            PublicProjectDetailQueryService projects,
            FrontendAssetResolver assetResolver,
            PublicContentResponseHeaders responseHeaders,
            PublicContentViewModelFactory contentViewModelFactory) {
        this.routeParser = Objects.requireNonNull(routeParser, "routeParser");
        this.routeResolver = Objects.requireNonNull(routeResolver, "routeResolver");
        this.projects = Objects.requireNonNull(projects, "projects");
        this.assetResolver = Objects.requireNonNull(assetResolver, "assetResolver");
        this.responseHeaders = Objects.requireNonNull(responseHeaders, "responseHeaders");
        this.contentViewModelFactory = Objects.requireNonNull(contentViewModelFactory, "contentViewModelFactory");
    }

    @GetMapping("/{language}/projects/{slug}")
    public ModelAndView project(
            @PathVariable("language") String language,
            @PathVariable("slug") String slug,
            HttpServletResponse response) {
        return routeParser.parse(language, slug)
                .map(routeResolver::resolve)
                .map(outcome -> handle(outcome, response))
                .orElseGet(() -> notFound(response));
    }

    private ModelAndView handle(DiscoveryPublicProjectRouteOutcome outcome, HttpServletResponse response) {
        return switch (outcome) {
            case DiscoveryPublicProjectRouteOutcome.Redirect redirect -> redirect(redirect, response);
            case DiscoveryPublicProjectRouteOutcome.Project project -> render(project, response);
            case DiscoveryPublicProjectRouteOutcome.NotFound ignored -> notFound(response);
        };
    }

    private ModelAndView redirect(DiscoveryPublicProjectRouteOutcome.Redirect redirect, HttpServletResponse response) {
        responseHeaders.applyPublicRedirectHeaders(response);
        response.setHeader("Location", redirect.targetPath());
        ModelAndView modelAndView = new ModelAndView();
        modelAndView.setStatus(HttpStatus.valueOf(redirect.statusCode()));
        return modelAndView;
    }

    private ModelAndView render(DiscoveryPublicProjectRouteOutcome.Project route, HttpServletResponse response) {
        return projects.find(route.projectId(), route.language(), route.slug())
                .map(project -> detailPage(project, route, response))
                .orElseGet(() -> notFound(response));
    }

    private ModelAndView detailPage(
            PublicProjectDetailView project,
            DiscoveryPublicProjectRouteOutcome.Project route,
            HttpServletResponse response) {
        responseHeaders.applyPublicContentHeaders(response);
        return new ModelAndView("site/projects/detail", "page", new PublicProjectDetailPage(
                project.title(),
                route.language().toLowerCase(Locale.ROOT),
                route.publicUrl(),
                route.canonicalUrl(),
                route.noindex(),
                PublicProjectPageCopy.forLanguage(route.language()),
                assetResolver.stylesheetPaths(MAIN_FRONTEND_ENTRY),
                project));
    }

    private ModelAndView notFound(HttpServletResponse response) {
        responseHeaders.applyPublicNotFoundHeaders(response);
        ModelAndView modelAndView =
                new ModelAndView("site/not-found", "page", contentViewModelFactory.notFoundPage());
        modelAndView.setStatus(HttpStatus.NOT_FOUND);
        return modelAndView;
    }
}
