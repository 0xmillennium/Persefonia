package dev.persefonia.webpublic.projects;

import dev.persefonia.profileportfolio.application.service.PublicProjectListingQueryService;
import dev.persefonia.webpublic.FrontendAssetResolver;
import dev.persefonia.webpublic.content.PublicCanonicalUrlFactory;
import dev.persefonia.webpublic.content.PublicContentResponseHeaders;
import dev.persefonia.webpublic.content.PublicContentViewModelFactory;
import dev.persefonia.webpublic.insights.PublicInsightSurface;
import dev.persefonia.webpublic.insights.PublicInsightsObservationGateway;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.ModelAndView;

@Controller
public final class PublicProjectListingController {
    private static final String MAIN_FRONTEND_ENTRY = "src/main.ts";

    private final PublicProjectListingQueryService projects;
    private final FrontendAssetResolver assetResolver;
    private final PublicCanonicalUrlFactory canonicalUrlFactory;
    private final PublicContentResponseHeaders responseHeaders;
    private final PublicContentViewModelFactory contentViewModelFactory;
    private final PublicInsightsObservationGateway insights;

    public PublicProjectListingController(
            PublicProjectListingQueryService projects,
            FrontendAssetResolver assetResolver,
            PublicCanonicalUrlFactory canonicalUrlFactory,
            PublicContentResponseHeaders responseHeaders,
            PublicContentViewModelFactory contentViewModelFactory,
            PublicInsightsObservationGateway insights) {
        this.projects = Objects.requireNonNull(projects, "projects");
        this.assetResolver = Objects.requireNonNull(assetResolver, "assetResolver");
        this.canonicalUrlFactory = Objects.requireNonNull(canonicalUrlFactory, "canonicalUrlFactory");
        this.responseHeaders = Objects.requireNonNull(responseHeaders, "responseHeaders");
        this.contentViewModelFactory = Objects.requireNonNull(contentViewModelFactory, "contentViewModelFactory");
        this.insights = Objects.requireNonNull(insights, "insights");
    }

    @GetMapping("/{language}/projects")
    public ModelAndView projects(@PathVariable("language") String language, HttpServletResponse response) {
        String parsedLanguage = language(language);
        if (parsedLanguage == null) {
            return notFound(response);
        }
        String publicUrl = "/" + parsedLanguage.toLowerCase(Locale.ROOT) + "/projects";
        responseHeaders.applyPublicContentHeaders(response);
        insights.recordPageView(PublicInsightSurface.PROJECT_INDEX);
        PublicProjectPageCopy copy = PublicProjectPageCopy.forLanguage(parsedLanguage);
        return new ModelAndView("site/projects/list", "page", new PublicProjectListingPage(
                copy.projectsTitle(),
                parsedLanguage.toLowerCase(Locale.ROOT),
                publicUrl,
                canonicalUrlFactory.canonicalUrl(publicUrl),
                false,
                copy,
                assetResolver.stylesheetPaths(MAIN_FRONTEND_ENTRY),
                projects.list(parsedLanguage)));
    }

    private ModelAndView notFound(HttpServletResponse response) {
        responseHeaders.applyPublicNotFoundHeaders(response);
        insights.recordNotFound();
        ModelAndView modelAndView =
                new ModelAndView("site/not-found", "page", contentViewModelFactory.notFoundPage());
        modelAndView.setStatus(HttpStatus.NOT_FOUND);
        return modelAndView;
    }

    private static String language(String value) {
        return switch (value == null ? "" : value) {
            case "tr" -> "TR";
            case "en" -> "EN";
            default -> null;
        };
    }
}
