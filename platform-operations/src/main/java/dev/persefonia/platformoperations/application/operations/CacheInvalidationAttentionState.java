package dev.persefonia.platformoperations.application.operations;

public enum CacheInvalidationAttentionState {
    PENDING_INITIAL,
    RUNNING,
    STRANDED,
    RETRY_AVAILABLE,
    RETRY_EXHAUSTED,
    COMPLETED
}
