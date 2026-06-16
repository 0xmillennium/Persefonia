package dev.persefonia.profileportfolio.domain.profile;

import dev.persefonia.profileportfolio.domain.common.PortfolioValidationException;
import java.util.Objects;

public record FocusAreaName(String value) {
    public FocusAreaName {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new PortfolioValidationException("focus area name must not be blank");
        }
    }

    public static FocusAreaName of(String value) {
        return new FocusAreaName(value);
    }
}
