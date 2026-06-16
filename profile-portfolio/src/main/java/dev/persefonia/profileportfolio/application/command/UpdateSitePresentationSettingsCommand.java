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
        String defaultTheme,
        boolean showFeaturedProjects,
        boolean showLatestWriting,
        boolean showResearchHighlights,
        int featuredProjectLimit,
        int latestWritingLimit,
        Instant requestedAt) {
    public UpdateSitePresentationSettingsCommand {
        Objects.requireNonNull(actor, "actor");
        supportedLanguages = Set.copyOf(Objects.requireNonNull(supportedLanguages, "supportedLanguages"));
        Objects.requireNonNull(requestedAt, "requestedAt");
    }
}
