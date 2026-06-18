package dev.persefonia.profileportfolio.domain.cv;

import dev.persefonia.profileportfolio.domain.common.PortfolioValidationException;

public record CvDisplayLabel(String value) {
    private static final int MAX_LENGTH = 160;

    public CvDisplayLabel {
        if (value == null || value.isBlank()) {
            throw new PortfolioValidationException("CV display label must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new PortfolioValidationException("CV display label must be at most 160 characters");
        }
    }

    public static CvDisplayLabel of(String value) {
        return new CvDisplayLabel(value);
    }
}
