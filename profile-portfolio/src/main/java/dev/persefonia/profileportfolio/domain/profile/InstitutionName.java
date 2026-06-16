package dev.persefonia.profileportfolio.domain.profile;

import dev.persefonia.profileportfolio.domain.common.PortfolioValidationException;
import java.util.Objects;

public record InstitutionName(String value) {
    public InstitutionName {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new PortfolioValidationException("institution must not be blank");
        }
    }

    public static InstitutionName of(String value) {
        return new InstitutionName(value);
    }
}
