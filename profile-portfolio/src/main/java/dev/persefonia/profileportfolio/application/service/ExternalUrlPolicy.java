package dev.persefonia.profileportfolio.application.service;

import dev.persefonia.profileportfolio.domain.common.ExternalUrl;
import dev.persefonia.profileportfolio.domain.common.PortfolioValidationException;

public final class ExternalUrlPolicy {
    private ExternalUrlPolicy() {
    }

    public static boolean accepts(String value) {
        try {
            ExternalUrl.of(value);
            return true;
        } catch (PortfolioValidationException exception) {
            return false;
        }
    }
}
