package dev.persefonia.webpublic;

import dev.persefonia.profileportfolio.application.query.PublicHomepageSettingsView;
import dev.persefonia.profileportfolio.application.query.PublicProfileSummaryView;
import dev.persefonia.profileportfolio.application.service.PublicFeaturedProjectQueryService;
import dev.persefonia.profileportfolio.application.service.PublicHomepageSettingsQueryService;
import dev.persefonia.profileportfolio.application.service.PublicProfileSummaryQueryService;
import dev.persefonia.webpublic.content.PublicCanonicalUrlFactory;
import dev.persefonia.webpublic.insights.PublicInsightSurface;
import dev.persefonia.webpublic.insights.PublicInsightsObservationGateway;
import dev.persefonia.webpublic.projects.PublicProjectPageCopy;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PublicHomeController {
    private static final String FRONTEND_ENTRY = "src/main.ts";

    private final FrontendAssetResolver assetResolver;
    private final PublicHomepageSettingsQueryService settings;
    private final PublicProfileSummaryQueryService profiles;
    private final PublicFeaturedProjectQueryService featuredProjects;
    private final PublicCanonicalUrlFactory canonicalUrlFactory;
    private final PublicInsightsObservationGateway insights;
    private final String publicBaseUrl;
    private final String ownerAlias;

    public PublicHomeController(
            FrontendAssetResolver assetResolver,
            PublicHomepageSettingsQueryService settings,
            PublicProfileSummaryQueryService profiles,
            PublicFeaturedProjectQueryService featuredProjects,
            PublicCanonicalUrlFactory canonicalUrlFactory,
            PublicInsightsObservationGateway insights,
            @Value("${site.public-base-url}") String publicBaseUrl,
            @Value("${site.owner-alias}") String ownerAlias) {
        this.assetResolver = Objects.requireNonNull(assetResolver, "assetResolver");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.featuredProjects = Objects.requireNonNull(featuredProjects, "featuredProjects");
        this.canonicalUrlFactory = Objects.requireNonNull(canonicalUrlFactory, "canonicalUrlFactory");
        this.insights = Objects.requireNonNull(insights, "insights");
        this.publicBaseUrl = publicBaseUrl;
        this.ownerAlias = ownerAlias;
    }

    @GetMapping("/")
    public String home(Model model) {
        insights.recordPageView(PublicInsightSurface.HOME);
        PublicHomepageSettingsView homepage = settings.current();
        Optional<PublicProfileSummaryView> profile = profiles.currentSummary(homepage.defaultLanguage());
        var homepageFeaturedProjects = homepage.showFeaturedProjects()
                ? featuredProjects.list(homepage.defaultLanguage(), homepage.featuredProjectLimit())
                : List.<dev.persefonia.profileportfolio.application.query.PublicFeaturedProjectView>of();
        model.addAttribute("page", new PublicHomeViewModel(
                homepage.siteName(),
                pageTitle(homepage),
                homepage.defaultLanguage(),
                homepage.defaultMetaDescription(),
                homepage.defaultTheme(),
                ownerAlias,
                publicBaseUrl,
                canonicalUrlFactory.canonicalUrl("/"),
                profile.isPresent(),
                profile,
                homepage.showFeaturedProjects(),
                homepageFeaturedProjects,
                PublicProjectPageCopy.forLanguage(homepage.defaultLanguage()).featuredProjectsTitle(),
                homepage.showLatestWriting(),
                homepage.showResearchHighlights(),
                homepage.featuredProjectLimit(),
                homepage.latestWritingLimit(),
                assetResolver.scriptPath(FRONTEND_ENTRY),
                assetResolver.stylesheetPaths(FRONTEND_ENTRY)));
        return "site/home";
    }

    private static String pageTitle(PublicHomepageSettingsView settings) {
        if (settings.titleSuffix() == null || settings.titleSuffix().isBlank()) {
            return settings.siteName();
        }
        return settings.siteName() + " " + settings.titleSuffix();
    }

}
