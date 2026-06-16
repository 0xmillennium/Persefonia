package dev.persefonia.profileportfolio.domain.profile;

import dev.persefonia.profileportfolio.domain.common.PortfolioValidationException;
import java.util.Objects;

public record FocusAreaDescription(String value) {
    public FocusAreaDescription {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new PortfolioValidationException("focus area description must not be blank");
        }
    }

    public static FocusAreaDescription of(String value) {
        return new FocusAreaDescription(value);
    }
}
