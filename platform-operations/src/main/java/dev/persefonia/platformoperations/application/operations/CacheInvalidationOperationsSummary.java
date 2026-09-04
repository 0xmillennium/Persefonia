package dev.persefonia.platformoperations.application.operations;

public record CacheInvalidationOperationsSummary(
        long requested,
        long running,
        long stranded,
        long retryAvailable,
        long retryExhausted,
        long completed) {
    public CacheInvalidationOperationsSummary {
        if (requested < 0 || running < 0 || stranded < 0 || retryAvailable < 0 || retryExhausted < 0 || completed < 0) {
            throw new IllegalArgumentException("operations summary counts cannot be negative");
        }
    }
}
