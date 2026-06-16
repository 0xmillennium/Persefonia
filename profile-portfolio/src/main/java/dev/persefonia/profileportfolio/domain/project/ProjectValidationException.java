package dev.persefonia.profileportfolio.domain.project;

import dev.persefonia.profileportfolio.domain.common.PortfolioValidationException;

public class ProjectValidationException extends PortfolioValidationException {
    public ProjectValidationException(String message) {
        super(message);
    }
}
