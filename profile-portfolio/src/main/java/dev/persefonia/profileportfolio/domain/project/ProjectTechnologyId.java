package dev.persefonia.profileportfolio.domain.project;

import java.util.Objects;
import java.util.UUID;

public record ProjectTechnologyId(UUID value) {
    public ProjectTechnologyId {
        Objects.requireNonNull(value, "value");
    }

    public static ProjectTechnologyId from(UUID value) {
        return new ProjectTechnologyId(value);
    }

    public static ProjectTechnologyId newId() {
        return new ProjectTechnologyId(UUID.randomUUID());
    }
}
