package dev.persefonia.platformoperations.application.operations;

import java.util.List;
import java.util.Objects;

public record CacheInvalidationOperationsListPage(
        List<CacheInvalidationOperationsListItem> items,
        long totalItems,
        int page,
        int pageSize) {
    public CacheInvalidationOperationsListPage {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        if (totalItems < 0 || page < 1 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("invalid operations page");
        }
    }
}
