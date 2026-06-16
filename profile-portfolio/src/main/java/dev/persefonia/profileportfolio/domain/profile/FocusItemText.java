package dev.persefonia.profileportfolio.domain.profile;

import dev.persefonia.profileportfolio.domain.common.PortfolioValidationException;
import java.util.Objects;

public record FocusItemText(String value) {
    public FocusItemText {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new PortfolioValidationException("focus item text must not be blank");
        }
    }

    public static FocusItemText of(String value) {
        return new FocusItemText(value);
    }
}
