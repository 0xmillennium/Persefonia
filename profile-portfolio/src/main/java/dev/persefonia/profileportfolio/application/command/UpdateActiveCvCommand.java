package dev.persefonia.profileportfolio.application.command;

import dev.persefonia.profileportfolio.application.authorization.PortfolioCommandActor;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record UpdateActiveCvCommand(
        PortfolioCommandActor actor,
        List<ActiveCvSelectionInput> selections,
        Instant requestedAt) {
    public UpdateActiveCvCommand {
        Objects.requireNonNull(actor, "actor");
        selections = List.copyOf(Objects.requireNonNull(selections, "selections"));
        Objects.requireNonNull(requestedAt, "requestedAt");
    }
}
