package dev.persefonia.profileportfolio.domain.common;

public record SortOrder(int value) {
    public SortOrder {
        if (value <= 0) {
            throw new PortfolioValidationException("sort order must be positive");
        }
    }

    public static SortOrder of(int value) {
        return new SortOrder(value);
    }
}
