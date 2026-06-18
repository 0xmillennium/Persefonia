package dev.persefonia.medialibrary.application.authorization;

import java.util.Objects;
import java.util.UUID;

public record MediaCommandActor(UUID identityRef, boolean active, boolean owner) {
    public MediaCommandActor {
        Objects.requireNonNull(identityRef, "identityRef");
    }
}
