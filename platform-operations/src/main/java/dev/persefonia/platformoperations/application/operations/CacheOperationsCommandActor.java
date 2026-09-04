package dev.persefonia.platformoperations.application.operations;

import java.util.Objects;
import java.util.UUID;

public record CacheOperationsCommandActor(UUID identityRef, boolean active, boolean owner) {
    public CacheOperationsCommandActor {
        Objects.requireNonNull(identityRef, "identityRef");
    }
}
