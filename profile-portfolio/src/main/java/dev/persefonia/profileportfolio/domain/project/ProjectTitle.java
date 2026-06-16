package dev.persefonia.profileportfolio.domain.project;

import java.util.Objects;

public record ProjectTitle(String value) {
    public ProjectTitle {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new ProjectValidationException("project title must not be blank");
        }
    }

    public static ProjectTitle of(String value) {
        return new ProjectTitle(value);
    }
}
