package dev.persefonia.profileportfolio.domain.settings;

import dev.persefonia.profileportfolio.domain.common.PortfolioValidationException;

public record PositiveInteger(int value) {
    public PositiveInteger {
        if (value <= 0) {
            throw new PortfolioValidationException("value must be positive");
        }
    }

    public static PositiveInteger of(int value) {
        return new PositiveInteger(value);
    }
}
