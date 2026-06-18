package dev.persefonia.medialibrary.application.storage;

public final class StorageWriteException extends RuntimeException {
    public StorageWriteException(String message, Throwable cause) {
        super(message, cause);
    }

    public StorageWriteException(String message) {
        super(message);
    }
}
