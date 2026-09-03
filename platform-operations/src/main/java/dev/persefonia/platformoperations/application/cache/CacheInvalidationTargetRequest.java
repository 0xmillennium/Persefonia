package dev.persefonia.platformoperations.application.cache;

import dev.persefonia.platformoperations.domain.cache.CacheTargetType;
import java.util.Objects;

public record CacheInvalidationTargetRequest(CacheTargetType targetType, String value) {
    public CacheInvalidationTargetRequest {
        Objects.requireNonNull(targetType, "targetType");
        Objects.requireNonNull(value, "value");
    }
}
