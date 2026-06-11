package dev.persefonia.app.contentpublishing.persistence;

final class ContentPublishingPersistenceException extends RuntimeException {
    ContentPublishingPersistenceException(String message) {
        super(message);
    }

    ContentPublishingPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
