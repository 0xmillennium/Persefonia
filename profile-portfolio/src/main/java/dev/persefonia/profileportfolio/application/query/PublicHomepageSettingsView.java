package dev.persefonia.profileportfolio.application.query;

public record PublicHomepageSettingsView(
        String siteName,
        String defaultLanguage,
        String titleSuffix,
        String defaultMetaDescription,
        boolean promoteFeaturedProjects,
        boolean promoteLatestWriting,
        boolean promoteResearchHighlights,
        int featuredProjectLimit,
        int latestWritingLimit,
        int researchHighlightLimit) {
}
