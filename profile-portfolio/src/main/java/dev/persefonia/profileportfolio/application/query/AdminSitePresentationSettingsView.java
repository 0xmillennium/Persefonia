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
        String defaultTheme,
        boolean showFeaturedProjects,
        boolean showLatestWriting,
        boolean showResearchHighlights,
        int featuredProjectLimit,
        int latestWritingLimit,
        Instant updatedAt,
        long version) {
    public AdminSitePresentationSettingsView {
        supportedLanguages = Set.copyOf(supportedLanguages);
    }
}
