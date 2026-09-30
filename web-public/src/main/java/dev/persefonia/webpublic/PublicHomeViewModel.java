package dev.persefonia.webpublic;

import dev.persefonia.profileportfolio.application.query.PublicProfileSummaryView;
import dev.persefonia.profileportfolio.application.query.PublicFeaturedProjectView;
import dev.persefonia.discovery.application.index.PublicHomepageContentEntry;
import java.util.List;
import java.util.Optional;

public record PublicHomeViewModel(
        String siteName,
        String pageTitle,
        String defaultLanguage,
        String defaultMetaDescription,
        String defaultTheme,
        String ownerAlias,
        String publicBaseUrl,
        String canonicalUrl,
        boolean profileAvailable,
        Optional<PublicProfileSummaryView> profile,
        boolean showFeaturedProjects,
        List<PublicFeaturedProjectView> featuredProjects,
        String featuredProjectsTitle,
        boolean showLatestWriting,
        boolean showResearchHighlights,
        List<PublicHomepageContentEntry> latestWriting,
        List<PublicHomepageContentEntry> researchHighlights,
        int featuredProjectLimit,
        int latestWritingLimit,
        String scriptPath,
        List<String> stylesheetPaths) {
    public PublicHomeViewModel {
        profile = profile == null ? Optional.empty() : profile;
        featuredProjects = List.copyOf(featuredProjects);
        latestWriting = List.copyOf(latestWriting);
        researchHighlights = List.copyOf(researchHighlights);
        java.util.Objects.requireNonNull(canonicalUrl, "canonicalUrl");
        java.util.Objects.requireNonNull(featuredProjectsTitle, "featuredProjectsTitle");
        stylesheetPaths = List.copyOf(stylesheetPaths);
    }
}
