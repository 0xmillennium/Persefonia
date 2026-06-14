package dev.persefonia.app.discovery.persistence;

final class DiscoveryPersistenceException extends RuntimeException {
    DiscoveryPersistenceException(String message) {
        super(message);
    }

    DiscoveryPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
