package dev.persefonia.webpublic;

import dev.persefonia.profileportfolio.application.query.PublicHomepageSettingsView;
import dev.persefonia.profileportfolio.application.service.PublicHomepageSettingsQueryService;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PublicHomeController {
    private static final String FRONTEND_ENTRY = "src/main.ts";

    private final FrontendAssetResolver assetResolver;
    private final PublicHomepageSettingsQueryService settings;
    private final String publicBaseUrl;
    private final String ownerAlias;

    public PublicHomeController(
            FrontendAssetResolver assetResolver,
            PublicHomepageSettingsQueryService settings,
            @Value("${site.public-base-url}") String publicBaseUrl,
            @Value("${site.owner-alias}") String ownerAlias) {
        this.assetResolver = Objects.requireNonNull(assetResolver, "assetResolver");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.publicBaseUrl = publicBaseUrl;
        this.ownerAlias = ownerAlias;
    }

    @GetMapping("/")
    public String home(Model model) {
        PublicHomepageSettingsView homepage = settings.current();
        model.addAttribute("page", new PublicHomeViewModel(
                homepage.siteName(),
                pageTitle(homepage),
                homepage.defaultLanguage(),
                homepage.defaultMetaDescription(),
                homepage.defaultTheme(),
                ownerAlias,
                publicBaseUrl,
                false,
                homepage.showFeaturedProjects(),
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
