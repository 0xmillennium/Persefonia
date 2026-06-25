package dev.persefonia.communication.application.authorization;

import java.util.Objects;
import java.util.UUID;

public record ContactMessageCommandActor(UUID identityRef, boolean active, boolean owner) {
    public ContactMessageCommandActor {
        Objects.requireNonNull(identityRef, "identityRef");
    }
}
