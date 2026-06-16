package dev.persefonia.profileportfolio.application.query;

public record PublicHomepageSettingsView(
        String siteName,
        String defaultLanguage,
        String titleSuffix,
        String defaultMetaDescription,
        String defaultTheme,
        boolean showFeaturedProjects,
        boolean showLatestWriting,
        boolean showResearchHighlights,
        int featuredProjectLimit,
        int latestWritingLimit) {
}
