package dev.persefonia.profileportfolio.domain.project;

import java.util.Objects;

public record NormalizedTechnologyName(String value) {
    public NormalizedTechnologyName {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new ProjectValidationException("normalized technology name must not be blank");
        }
    }

    public static NormalizedTechnologyName of(String value) {
        return new NormalizedTechnologyName(value);
    }
}
