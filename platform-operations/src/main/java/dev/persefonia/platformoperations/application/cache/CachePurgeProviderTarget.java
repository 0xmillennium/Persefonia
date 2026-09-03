package dev.persefonia.platformoperations.application.cache;

import dev.persefonia.platformoperations.domain.cache.CacheInvalidationTargetId;
import dev.persefonia.platformoperations.domain.cache.CacheTargetType;
import dev.persefonia.platformoperations.domain.cache.CacheTargetValue;
import java.util.Objects;

public record CachePurgeProviderTarget(
        CacheInvalidationTargetId targetId,
        CacheTargetType targetType,
        CacheTargetValue targetValue) {
    public CachePurgeProviderTarget {
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(targetType, "targetType");
        Objects.requireNonNull(targetValue, "targetValue");
        CacheTargetValue.of(targetType, targetValue.value());
    }
}
