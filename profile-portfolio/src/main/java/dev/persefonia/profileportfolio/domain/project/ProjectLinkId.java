package dev.persefonia.profileportfolio.domain.project;

import java.util.Objects;
import java.util.UUID;

public record ProjectLinkId(UUID value) {
    public ProjectLinkId {
        Objects.requireNonNull(value, "value");
    }

    public static ProjectLinkId from(UUID value) {
        return new ProjectLinkId(value);
    }

    public static ProjectLinkId newId() {
        return new ProjectLinkId(UUID.randomUUID());
    }
}
