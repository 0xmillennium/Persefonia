package dev.persefonia.profileportfolio.application.command;

import dev.persefonia.profileportfolio.application.authorization.PortfolioCommandActor;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record UpsertActivePersonalProfileCommand(
        PortfolioCommandActor actor,
        String displayName,
        List<ProfileLocalizationInput> localizations,
        List<ExternalProfileLinkInput> externalLinks,
        Instant requestedAt) {
    public UpsertActivePersonalProfileCommand {
        Objects.requireNonNull(actor, "actor");
        localizations = List.copyOf(Objects.requireNonNull(localizations, "localizations"));
        externalLinks = List.copyOf(Objects.requireNonNull(externalLinks, "externalLinks"));
        Objects.requireNonNull(requestedAt, "requestedAt");
    }
}
