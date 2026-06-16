package dev.persefonia.profileportfolio.domain.common;

import java.util.Objects;

public record LinkLabel(String value) {
    public LinkLabel {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new PortfolioValidationException("link label must not be blank");
        }
    }

    public static LinkLabel of(String value) {
        return new LinkLabel(value);
    }
}
