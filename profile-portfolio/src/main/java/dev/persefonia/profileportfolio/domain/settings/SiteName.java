package dev.persefonia.profileportfolio.domain.settings;

import dev.persefonia.profileportfolio.domain.common.PortfolioValidationException;
import java.util.Objects;

public record SiteName(String value) {
    public SiteName {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new PortfolioValidationException("site name must not be blank");
        }
    }

    public static SiteName of(String value) {
        return new SiteName(value);
    }
}
