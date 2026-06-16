package dev.persefonia.profileportfolio.domain.project;

import java.util.Objects;
import java.util.UUID;

public record ProjectId(UUID value) {
    public ProjectId {
        Objects.requireNonNull(value, "value");
    }

    public static ProjectId from(UUID value) {
        return new ProjectId(value);
    }

    public static ProjectId newId() {
        return new ProjectId(UUID.randomUUID());
    }
}
