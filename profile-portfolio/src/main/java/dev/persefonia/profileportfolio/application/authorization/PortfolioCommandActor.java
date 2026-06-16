package dev.persefonia.profileportfolio.application.authorization;

import java.util.Objects;
import java.util.UUID;

public record PortfolioCommandActor(UUID identityRef, boolean active, boolean owner) {
    public PortfolioCommandActor {
        Objects.requireNonNull(identityRef, "identityRef");
    }
}
