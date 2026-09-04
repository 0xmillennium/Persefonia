package dev.persefonia.platformoperations.application.operations;

import dev.persefonia.platformoperations.domain.cache.CacheInvalidationStatus;

public record CacheInvalidationOperationsSearchRequest(
        CacheInvalidationStatus status,
        int page,
        int pageSize) {
    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_PAGE_SIZE = 25;
    public static final int MAX_PAGE_SIZE = 100;

    public CacheInvalidationOperationsSearchRequest {
        if (page < 1) throw new IllegalArgumentException("page must be at least 1");
        if (pageSize != 25 && pageSize != 50 && pageSize != 100) {
            throw new IllegalArgumentException("page size must be 25, 50, or 100");
        }
    }

    public static CacheInvalidationOperationsSearchRequest defaults() {
        return new CacheInvalidationOperationsSearchRequest(null, DEFAULT_PAGE, DEFAULT_PAGE_SIZE);
    }
}
