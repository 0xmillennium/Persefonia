package dev.persefonia.profileportfolio.application.query;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record AdminSitePresentationSettingsView(
        UUID id,
        String siteName,
        String defaultLanguage,
        Set<String> supportedLanguages,
        String titleSuffix,
        String defaultMetaDescription,
        String standardPublicTheme,
        boolean promoteFeaturedProjects,
        boolean promoteLatestWriting,
        boolean promoteResearchHighlights,
        int featuredProjectLimit,
        int latestWritingLimit,
        int researchHighlightLimit,
        Instant updatedAt,
        long version) {
    public AdminSitePresentationSettingsView {
        supportedLanguages = Set.copyOf(supportedLanguages);
    }
}
