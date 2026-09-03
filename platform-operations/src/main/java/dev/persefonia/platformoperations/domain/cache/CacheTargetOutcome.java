package dev.persefonia.platformoperations.domain.cache;

import java.util.Objects;

public record CacheTargetOutcome(CacheInvalidationTargetId targetId, CacheTargetStatus status) {
    public CacheTargetOutcome {
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(status, "status");
        if (status == CacheTargetStatus.PENDING) {
            throw new CacheInvalidationValidationException("target outcome must be terminal for this attempt");
        }
    }

    public static CacheTargetOutcome of(CacheInvalidationTargetId targetId, CacheTargetStatus status) {
        return new CacheTargetOutcome(targetId, status);
    }
}
