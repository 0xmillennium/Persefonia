package dev.persefonia.platformoperations.domain.cache;

import java.util.Objects;
import java.util.UUID;

public record CacheInvalidationBatchId(UUID value) {
    public CacheInvalidationBatchId { Objects.requireNonNull(value, "value"); }
    public static CacheInvalidationBatchId newId() { return new CacheInvalidationBatchId(UUID.randomUUID()); }
    public static CacheInvalidationBatchId from(UUID value) { return new CacheInvalidationBatchId(value); }
}
