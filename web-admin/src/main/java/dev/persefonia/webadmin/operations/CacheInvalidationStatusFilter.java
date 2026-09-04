package dev.persefonia.webadmin.operations;

import dev.persefonia.platformoperations.domain.cache.CacheInvalidationStatus;

public record CacheInvalidationStatusFilter(CacheInvalidationStatus status) {
    public String value() { return status == null ? "" : status.name(); }
}
