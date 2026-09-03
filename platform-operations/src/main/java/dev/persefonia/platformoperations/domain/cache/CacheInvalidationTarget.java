package dev.persefonia.platformoperations.domain.cache;

import java.util.Objects;

public final class CacheInvalidationTarget {
    private final CacheInvalidationTargetId id;
    private final CacheTargetType targetType;
    private final CacheTargetValue value;
    private CacheTargetStatus status;

    private CacheInvalidationTarget(CacheInvalidationTargetId id, CacheTargetType targetType, CacheTargetValue value,
            CacheTargetStatus status) {
        this.id = Objects.requireNonNull(id, "id");
        this.targetType = Objects.requireNonNull(targetType, "targetType");
        this.value = Objects.requireNonNull(value, "value");
        this.status = Objects.requireNonNull(status, "status");
        CacheTargetValue.of(targetType, value.value());
    }

    public static CacheInvalidationTarget pending(CacheInvalidationTargetId id, CacheTargetType type,
            CacheTargetValue value) {
        return new CacheInvalidationTarget(id, type, value, CacheTargetStatus.PENDING);
    }

    public static CacheInvalidationTarget rehydrate(CacheInvalidationTargetId id, CacheTargetType type,
            CacheTargetValue value, CacheTargetStatus status) {
        return new CacheInvalidationTarget(id, type, value, status);
    }

    void changeStatus(CacheTargetStatus status) { this.status = Objects.requireNonNull(status, "status"); }
    public CacheInvalidationTargetId id() { return id; }
    public CacheTargetType targetType() { return targetType; }
    public CacheTargetValue value() { return value; }
    public CacheTargetStatus status() { return status; }
}
