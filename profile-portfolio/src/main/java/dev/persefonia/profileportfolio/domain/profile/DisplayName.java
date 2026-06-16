package dev.persefonia.profileportfolio.domain.profile;

import dev.persefonia.profileportfolio.domain.common.PortfolioValidationException;
import java.util.Objects;

public record DisplayName(String value) {
    public DisplayName {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new PortfolioValidationException("display name must not be blank");
        }
    }

    public static DisplayName of(String value) {
        return new DisplayName(value);
    }
}
