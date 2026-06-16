package dev.persefonia.webpublic;

import dev.persefonia.profileportfolio.application.query.PublicProfileSummaryView;
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
        boolean profileAvailable,
        Optional<PublicProfileSummaryView> profile,
        boolean showFeaturedProjects,
        boolean showLatestWriting,
        boolean showResearchHighlights,
        int featuredProjectLimit,
        int latestWritingLimit,
        String scriptPath,
        List<String> stylesheetPaths) {
    public PublicHomeViewModel {
        profile = profile == null ? Optional.empty() : profile;
        stylesheetPaths = List.copyOf(stylesheetPaths);
    }
}
