package dev.persefonia.webpublic;

import dev.persefonia.contentpublishing.application.query.PublicContentBySourceQuery;
import dev.persefonia.contentpublishing.application.service.PublicContentBySourceQueryHandler;
import dev.persefonia.discovery.application.contract.DiscoveryLanguage;
import dev.persefonia.discovery.application.index.PublicHomepageContentEntry;
import dev.persefonia.discovery.application.index.PublicHomepageContentQueryService;
import dev.persefonia.medialibrary.application.publicview.PublicImageAssetQueryService;
import dev.persefonia.profileportfolio.application.query.PublicHomepageSettingsView;
import dev.persefonia.profileportfolio.application.service.PublicFeaturedProjectQueryService;
import dev.persefonia.profileportfolio.application.service.PublicHomepageSettingsQueryService;
import dev.persefonia.webpublic.content.PublicCanonicalUrlFactory;
import dev.persefonia.webpublic.content.PublicContentResponseHeaders;
import dev.persefonia.webpublic.insights.PublicInsightSurface;
import dev.persefonia.webpublic.insights.PublicInsightsObservationGateway;
import dev.persefonia.webpublic.projects.PublicProjectPageCopy;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PublicHomeController {
    private static final String FRONTEND_ENTRY = "src/home.ts";

    private final FrontendAssetResolver assetResolver;
    private final PublicHomepageSettingsQueryService settings;
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
        var homepageFeaturedProjects = homepage.promoteFeaturedProjects()
                ? featuredProjects.list(homepage.defaultLanguage(), homepage.featuredProjectLimit())
                : List.<dev.persefonia.profileportfolio.application.query.PublicFeaturedProjectView>of();
        DiscoveryLanguage language = DiscoveryLanguage.valueOf(homepage.defaultLanguage());
        List<PublicHomepageContentEntry> latestWriting = homepage.promoteLatestWriting()
                ? homepageContent.latestWriting(language, homepage.latestWritingLimit())
                : List.of();
        List<PublicHomepageContentEntry> articles = homepage.promoteLatestWriting()
                ? homepageContent.latestArticles(language, homepage.latestWritingLimit()) : List.of();
        List<PublicHomepageContentEntry> notes = homepage.promoteLatestWriting()
                ? homepageContent.latestNotes(language, homepage.latestWritingLimit()) : List.of();
        List<PublicHomepageContentEntry> researchHighlights = homepage.promoteResearchHighlights()
                ? homepageContent.latestResearch(language, homepage.researchHighlightLimit())
                : List.of();
        var covers = new LinkedHashMap<String, PublicHomeCoverView>();
        // Media storage is optional in the supported test/development configuration.
        var imageQuery = images.getIfAvailable();
        if (imageQuery != null) {
            homepageFeaturedProjects.forEach(project -> project.coverAssetId()
                    .flatMap(id -> cover(imageQuery, id))
                    .ifPresent(image -> covers.put(project.publicUrl(), image)));
        }
        var tags = new LinkedHashMap<String, List<String>>();
        java.util.stream.Stream.of(articles, notes, researchHighlights).flatMap(List::stream).forEach(entry -> entry.sourceId()
                .flatMap(id -> contentGallery.galleryMetadata(new PublicContentBySourceQuery(id, entry.publicUrl())))
                .ifPresent(metadata -> {
                    tags.put(entry.publicUrl(), metadata.tags());
                    if (imageQuery != null) metadata.coverAssetId().flatMap(id -> cover(imageQuery, id))
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
                homepage.promoteFeaturedProjects(),
                homepageFeaturedProjects,
                PublicProjectPageCopy.forLanguage(homepage.defaultLanguage()).featuredProjectsTitle(),
                homepage.promoteLatestWriting(),
                homepage.promoteResearchHighlights(),
                latestWriting,
                articles,
                notes,
                researchHighlights,
                covers,
                tags,
                assetResolver.scriptPath(FRONTEND_ENTRY),
                assetResolver.stylesheetPaths(FRONTEND_ENTRY)));
        return "site/home";
    }

    private static Optional<PublicHomeCoverView> cover(PublicImageAssetQueryService images, String id) {
        var variants = images.findVariants(id, List.of("thumbnail", "medium", "large"));
        return variants.isEmpty() ? Optional.empty() : Optional.of(new PublicHomeCoverView(variants));
    }

    private static String pageTitle(PublicHomepageSettingsView settings) {
        if (settings.titleSuffix() == null || settings.titleSuffix().isBlank()) {
            return settings.siteName();
        }
        return settings.siteName() + " " + settings.titleSuffix();
    }

}
