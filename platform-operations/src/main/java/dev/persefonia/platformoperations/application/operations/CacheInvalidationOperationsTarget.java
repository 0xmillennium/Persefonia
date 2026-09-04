package dev.persefonia.platformoperations.application.operations;

import dev.persefonia.platformoperations.domain.cache.*;
import java.util.Objects;

public record CacheInvalidationOperationsTarget(
        CacheTargetType type,
        CacheTargetValue value,
        CacheTargetStatus status) {
    public CacheInvalidationOperationsTarget {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(status, "status");
    }
}
