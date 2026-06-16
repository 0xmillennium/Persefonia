package dev.persefonia.profileportfolio.domain.profile;

import dev.persefonia.profileportfolio.domain.common.PortfolioValidationException;
import java.util.Objects;

public record ShortBio(String value) {
    public ShortBio {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new PortfolioValidationException("short bio must not be blank");
        }
    }

    public static ShortBio of(String value) {
        return new ShortBio(value);
    }
}
