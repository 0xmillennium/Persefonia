package dev.persefonia.app.profileportfolio.persistence;

public class PortfolioPersistenceException extends RuntimeException {
    public PortfolioPersistenceException(String message) {
        super(message);
    }

    public PortfolioPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
