package dev.persefonia.webpublic;

import java.util.List;

public record PublicHomeViewModel(
        String siteName,
        String pageTitle,
        String defaultLanguage,
        String defaultMetaDescription,
        String defaultTheme,
        String ownerAlias,
        String publicBaseUrl,
        boolean profileAvailable,
        boolean showFeaturedProjects,
        boolean showLatestWriting,
        boolean showResearchHighlights,
        int featuredProjectLimit,
        int latestWritingLimit,
        String scriptPath,
        List<String> stylesheetPaths) {
    public PublicHomeViewModel {
        stylesheetPaths = List.copyOf(stylesheetPaths);
    }
}
