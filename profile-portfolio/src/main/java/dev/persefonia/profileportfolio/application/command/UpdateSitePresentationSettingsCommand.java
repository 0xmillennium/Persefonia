package dev.persefonia.profileportfolio.application.command;

import dev.persefonia.profileportfolio.application.authorization.PortfolioCommandActor;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public record UpdateSitePresentationSettingsCommand(
        PortfolioCommandActor actor,
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
        Instant requestedAt) {
    public UpdateSitePresentationSettingsCommand {
        Objects.requireNonNull(actor, "actor");
        supportedLanguages = Set.copyOf(Objects.requireNonNull(supportedLanguages, "supportedLanguages"));
        Objects.requireNonNull(requestedAt, "requestedAt");
    }
}
