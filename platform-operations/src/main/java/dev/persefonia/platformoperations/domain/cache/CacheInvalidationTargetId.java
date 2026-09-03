package dev.persefonia.platformoperations.domain.cache;

import java.util.Objects;
import java.util.UUID;

public record CacheInvalidationTargetId(UUID value) {
    public CacheInvalidationTargetId { Objects.requireNonNull(value, "value"); }
    public static CacheInvalidationTargetId newId() { return new CacheInvalidationTargetId(UUID.randomUUID()); }
    public static CacheInvalidationTargetId from(UUID value) { return new CacheInvalidationTargetId(value); }
}
