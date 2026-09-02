package dev.persefonia.discovery.application.authorization;

import java.util.Objects;
import java.util.UUID;

public record AdminRedirectCommandActor(UUID identityRef, boolean active, boolean owner) {
    public AdminRedirectCommandActor {
        Objects.requireNonNull(identityRef, "identityRef");
    }
}
