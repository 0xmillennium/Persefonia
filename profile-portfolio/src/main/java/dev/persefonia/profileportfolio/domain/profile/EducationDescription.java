package dev.persefonia.profileportfolio.domain.profile;

import dev.persefonia.profileportfolio.domain.common.PortfolioValidationException;
import java.util.Objects;

public record EducationDescription(String value) {
    public EducationDescription {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new PortfolioValidationException("education description must not be blank");
        }
    }

    public static EducationDescription of(String value) {
        return new EducationDescription(value);
    }
}
