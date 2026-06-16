package dev.persefonia.profileportfolio.domain.project;

import java.util.Objects;
import java.util.UUID;

public record ProjectLocalizationId(UUID value) {
    public ProjectLocalizationId {
        Objects.requireNonNull(value, "value");
    }

    public static ProjectLocalizationId from(UUID value) {
        return new ProjectLocalizationId(value);
    }

    public static ProjectLocalizationId newId() {
        return new ProjectLocalizationId(UUID.randomUUID());
    }
}
