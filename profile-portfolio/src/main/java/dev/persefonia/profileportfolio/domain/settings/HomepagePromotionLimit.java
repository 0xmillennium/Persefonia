package dev.persefonia.profileportfolio.domain.settings;

import dev.persefonia.profileportfolio.domain.common.PortfolioValidationException;

public record HomepagePromotionLimit(int value) {
    public HomepagePromotionLimit {
        if (value < 1 || value > 3) {
            throw new PortfolioValidationException("homepage promotion limit must be between 1 and 3");
        }
    }

    public static HomepagePromotionLimit of(int value) {
        return new HomepagePromotionLimit(value);
    }
}
