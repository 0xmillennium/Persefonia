package dev.persefonia.profileportfolio.domain.project;

import java.util.Objects;

public record ProjectSummary(String value) {
    public ProjectSummary {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new ProjectValidationException("project summary must not be blank");
        }
    }

    public static ProjectSummary of(String value) {
        return new ProjectSummary(value);
    }
}
