package dev.persefonia.platformoperations.domain.cache;

public final class CacheInvalidationValidationException extends IllegalArgumentException {
    public CacheInvalidationValidationException(String message) {
        super(message);
    }
}
