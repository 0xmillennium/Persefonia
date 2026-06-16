package dev.persefonia.profileportfolio.domain.profile;

import java.util.Objects;
import java.util.UUID;

public record TechnicalFocusAreaId(UUID value) {
    public TechnicalFocusAreaId {
        Objects.requireNonNull(value, "value");
    }

    public static TechnicalFocusAreaId from(UUID value) {
        return new TechnicalFocusAreaId(value);
    }

    public static TechnicalFocusAreaId newId() {
        return new TechnicalFocusAreaId(UUID.randomUUID());
    }
}
