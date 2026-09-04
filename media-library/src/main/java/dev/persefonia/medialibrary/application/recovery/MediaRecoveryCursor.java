package dev.persefonia.medialibrary.application.recovery;

import java.util.Objects;
import java.util.UUID;

public record MediaRecoveryCursor(MediaRecoveryObjectKind kind, UUID objectId) {
    public MediaRecoveryCursor {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(objectId, "objectId");
    }
}
