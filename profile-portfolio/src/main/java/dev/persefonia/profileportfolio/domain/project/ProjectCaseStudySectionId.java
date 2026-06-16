package dev.persefonia.profileportfolio.domain.project;

import java.util.Objects;
import java.util.UUID;

public record ProjectCaseStudySectionId(UUID value) {
    public ProjectCaseStudySectionId {
        Objects.requireNonNull(value, "value");
    }

    public static ProjectCaseStudySectionId from(UUID value) {
        return new ProjectCaseStudySectionId(value);
    }

    public static ProjectCaseStudySectionId newId() {
        return new ProjectCaseStudySectionId(UUID.randomUUID());
    }
}
