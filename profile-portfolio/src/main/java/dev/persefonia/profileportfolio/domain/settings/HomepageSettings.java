package dev.persefonia.profileportfolio.domain.settings;

import java.util.Objects;

public record HomepageSettings(
        boolean promoteFeaturedProjects,
        boolean promoteLatestWriting,
        boolean promoteResearchHighlights,
        HomepagePromotionLimit featuredProjectLimit,
        HomepagePromotionLimit latestWritingLimit,
        HomepagePromotionLimit researchHighlightLimit) {
    public HomepageSettings {
        Objects.requireNonNull(featuredProjectLimit, "featuredProjectLimit");
        Objects.requireNonNull(latestWritingLimit, "latestWritingLimit");
        Objects.requireNonNull(researchHighlightLimit, "researchHighlightLimit");
    }

    public static HomepageSettings of(
            boolean promoteFeaturedProjects,
            boolean promoteLatestWriting,
            boolean promoteResearchHighlights,
            HomepagePromotionLimit featuredProjectLimit,
            HomepagePromotionLimit latestWritingLimit,
            HomepagePromotionLimit researchHighlightLimit) {
        return new HomepageSettings(
                promoteFeaturedProjects,
                promoteLatestWriting,
                promoteResearchHighlights,
                featuredProjectLimit,
                latestWritingLimit,
                researchHighlightLimit);
    }
}
