package dev.persefonia.profileportfolio.domain.project;

import java.util.Objects;

public record TechnologyName(String value) {
    public TechnologyName {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new ProjectValidationException("technology name must not be blank");
        }
    }

    public static TechnologyName of(String value) {
        return new TechnologyName(value);
    }
}
