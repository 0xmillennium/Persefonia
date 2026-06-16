package dev.persefonia.profileportfolio.domain.settings;

import dev.persefonia.profileportfolio.domain.common.PortfolioValidationException;
import java.util.Objects;

public record SeoDescription(String value) {
    public SeoDescription {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new PortfolioValidationException("SEO description must not be blank");
        }
    }

    public static SeoDescription of(String value) {
        return new SeoDescription(value);
    }
}
