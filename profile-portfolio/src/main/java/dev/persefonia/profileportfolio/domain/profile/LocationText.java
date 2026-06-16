package dev.persefonia.profileportfolio.domain.profile;

import dev.persefonia.profileportfolio.domain.common.PortfolioValidationException;
import java.util.Objects;

public record LocationText(String value) {
    public LocationText {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new PortfolioValidationException("location text must not be blank");
        }
    }

    public static LocationText of(String value) {
        return new LocationText(value);
    }
}
