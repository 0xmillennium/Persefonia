package dev.persefonia.profileportfolio.domain.cv;

import java.util.Objects;
import java.util.UUID;

public record ActiveCvDocumentId(UUID value) {
    public ActiveCvDocumentId {
        Objects.requireNonNull(value, "value");
    }

    public static ActiveCvDocumentId newId() {
        return new ActiveCvDocumentId(UUID.randomUUID());
    }

    public static ActiveCvDocumentId from(UUID value) {
        return new ActiveCvDocumentId(value);
    }
}
