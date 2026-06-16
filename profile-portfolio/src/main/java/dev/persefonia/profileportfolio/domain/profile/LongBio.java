package dev.persefonia.profileportfolio.domain.profile;

import dev.persefonia.profileportfolio.domain.common.PortfolioValidationException;
import java.util.Objects;

public record LongBio(String value) {
    public LongBio {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new PortfolioValidationException("long bio must not be blank");
        }
    }

    public static LongBio of(String value) {
        return new LongBio(value);
    }
}
