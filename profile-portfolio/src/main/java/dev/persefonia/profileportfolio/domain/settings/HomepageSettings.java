package dev.persefonia.profileportfolio.domain.settings;

import java.util.Objects;

public record HomepageSettings(
        boolean showFeaturedProjects,
        boolean showLatestWriting,
        boolean showResearchHighlights,
        PositiveInteger featuredProjectLimit,
        PositiveInteger latestWritingLimit) {
    public HomepageSettings {
        Objects.requireNonNull(featuredProjectLimit, "featuredProjectLimit");
        Objects.requireNonNull(latestWritingLimit, "latestWritingLimit");
    }

    public static HomepageSettings of(
            boolean showFeaturedProjects,
            boolean showLatestWriting,
            boolean showResearchHighlights,
            PositiveInteger featuredProjectLimit,
            PositiveInteger latestWritingLimit) {
        return new HomepageSettings(
                showFeaturedProjects,
                showLatestWriting,
                showResearchHighlights,
                featuredProjectLimit,
                latestWritingLimit);
    }
}
