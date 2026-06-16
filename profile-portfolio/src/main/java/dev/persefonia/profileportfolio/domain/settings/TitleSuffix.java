package dev.persefonia.profileportfolio.domain.settings;

import dev.persefonia.profileportfolio.domain.common.PortfolioValidationException;
import java.util.Objects;

public record TitleSuffix(String value) {
    public TitleSuffix {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new PortfolioValidationException("title suffix must not be blank");
        }
    }

    public static TitleSuffix of(String value) {
        return new TitleSuffix(value);
    }
}
