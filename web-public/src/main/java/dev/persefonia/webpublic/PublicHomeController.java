package dev.persefonia.webpublic;

import org.springframework.beans.factory.ObjectProvider;
import dev.persefonia.medialibrary.application.publicview.PublicImageAssetQueryService;
import dev.persefonia.medialibrary.application.publicview.PublicImageVariantView;
import dev.persefonia.contentpublishing.application.service.PublicContentBySourceQueryHandler;
import dev.persefonia.contentpublishing.application.query.PublicContentBySourceQuery;
import java.util.LinkedHashMap;

import dev.persefonia.profileportfolio.application.query.PublicHomepageSettingsView;
import dev.persefonia.discovery.application.contract.DiscoveryLanguage;
import dev.persefonia.discovery.application.index.PublicHomepageContentEntry;
import dev.persefonia.discovery.application.index.PublicHomepageContentQueryService;
import dev.persefonia.profileportfolio.application.query.PublicProfileSummaryView;
import dev.persefonia.profileportfolio.application.service.PublicFeaturedProjectQueryService;
import dev.persefonia.profileportfolio.application.service.PublicHomepageSettingsQueryService;
import dev.persefonia.profileportfolio.application.service.PublicProfileSummaryQueryService;
import dev.persefonia.webpublic.content.PublicCanonicalUrlFactory;
import dev.persefonia.webpublic.content.PublicContentResponseHeaders;
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
import jakarta.servlet.http.HttpServletResponse;

@Controller
public class PublicHomeController {
    private static final String FRONTEND_ENTRY = "src/home.ts";

    private final FrontendAssetResolver assetResolver;
    private final PublicHomepageSettingsQueryService settings;
    private final PublicProfileSummaryQueryService profiles;
    private final PublicFeaturedProjectQueryService featuredProjects;
    private final PublicHomepageContentQueryService homepageContent;
    private final PublicCanonicalUrlFactory canonicalUrlFactory;
    private final PublicInsightsObservationGateway insights;
    private final PublicContentResponseHeaders responseHeaders;
    private final String ownerAlias;
    private final ObjectProvider<PublicImageAssetQueryService> images;
    private final PublicContentBySourceQueryHandler contentGallery;

    public PublicHomeController(
            FrontendAssetResolver assetResolver,
            PublicHomepageSettingsQueryService settings,
            PublicProfileSummaryQueryService profiles,
            PublicFeaturedProjectQueryService featuredProjects,
            PublicHomepageContentQueryService homepageContent,
            PublicCanonicalUrlFactory canonicalUrlFactory,
            PublicInsightsObservationGateway insights,
            PublicContentResponseHeaders responseHeaders,
            @Value("${site.owner-alias}") String ownerAlias,
            ObjectProvider<PublicImageAssetQueryService> images,
            PublicContentBySourceQueryHandler contentGallery) {
        this.assetResolver = Objects.requireNonNull(assetResolver, "assetResolver");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.featuredProjects = Objects.requireNonNull(featuredProjects, "featuredProjects");
        this.homepageContent = Objects.requireNonNull(homepageContent, "homepageContent");
        this.canonicalUrlFactory = Objects.requireNonNull(canonicalUrlFactory, "canonicalUrlFactory");
        this.insights = Objects.requireNonNull(insights, "insights");
        this.responseHeaders = Objects.requireNonNull(responseHeaders, "responseHeaders");
        this.ownerAlias = ownerAlias;
        this.images = Objects.requireNonNull(images);
        this.contentGallery = Objects.requireNonNull(contentGallery);
    }

    @GetMapping("/")
    public String home(Model model, HttpServletResponse response) {
        responseHeaders.applyPublicContentHeaders(response);
        insights.recordPageView(PublicInsightSurface.HOME);
        PublicHomepageSettingsView homepage = settings.current();
        Optional<PublicProfileSummaryView> profile = profiles.currentSummary(homepage.defaultLanguage());
        var homepageFeaturedProjects = homepage.promoteFeaturedProjects()
                ? featuredProjects.list(homepage.defaultLanguage(), homepage.featuredProjectLimit())
                : List.<dev.persefonia.profileportfolio.application.query.PublicFeaturedProjectView>of();
        DiscoveryLanguage language = DiscoveryLanguage.valueOf(homepage.defaultLanguage());
        List<PublicHomepageContentEntry> latestWriting = homepage.promoteLatestWriting()
                ? homepageContent.latestWriting(language, homepage.latestWritingLimit())
                : List.of();
        List<PublicHomepageContentEntry> researchHighlights = homepage.promoteResearchHighlights()
                ? homepageContent.latestResearch(language, homepage.researchHighlightLimit())
                : List.of();
        var covers = new LinkedHashMap<String,
                PublicImageVariantView>();
        // Media storage is optional in the supported test/development configuration.
        var imageQuery = images.getIfAvailable();
        if (imageQuery != null) {
            homepageFeaturedProjects.forEach(project -> project.coverAssetId()
                    .flatMap(id -> imageQuery.findVariant(id, "medium"))
                    .ifPresent(image -> covers.put(project.publicUrl(), image)));
        }
        var tags = new LinkedHashMap<String, List<String>>();
        latestWriting.forEach(entry -> entry.sourceId()
                .flatMap(id -> contentGallery.galleryMetadata(new PublicContentBySourceQuery(id, entry.publicUrl())))
                .ifPresent(metadata -> {
                    tags.put(entry.publicUrl(), metadata.tags());
                    if (imageQuery != null) metadata.coverAssetId().flatMap(id -> imageQuery.findVariant(id, "medium"))
                            .ifPresent(image -> covers.put(entry.publicUrl(), image));
                }));
        model.addAttribute("page", new PublicHomeViewModel(
                homepage.siteName(),
                pageTitle(homepage),
                homepage.defaultLanguage(),
                homepage.defaultMetaDescription(),
                ownerAlias,
                canonicalUrlFactory.canonicalUrl("/"),
                PublicHomePageCopy.forLanguage(homepage.defaultLanguage()),
                profile,
                homepage.promoteFeaturedProjects(),
                homepageFeaturedProjects,
                PublicProjectPageCopy.forLanguage(homepage.defaultLanguage()).featuredProjectsTitle(),
                homepage.promoteLatestWriting(),
                homepage.promoteResearchHighlights(),
                latestWriting,
                researchHighlights,
                covers,
                tags,
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
