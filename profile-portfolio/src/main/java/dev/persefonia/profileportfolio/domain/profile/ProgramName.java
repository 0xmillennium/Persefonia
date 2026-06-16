package dev.persefonia.profileportfolio.domain.profile;

import dev.persefonia.profileportfolio.domain.common.PortfolioValidationException;
import java.util.Objects;

public record ProgramName(String value) {
    public ProgramName {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new PortfolioValidationException("program must not be blank");
        }
    }

    public static ProgramName of(String value) {
        return new ProgramName(value);
    }
}
