package dev.persefonia.platformoperations.application.cache;

import dev.persefonia.platformoperations.domain.cache.CacheInvalidationBatchId;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationTargetId;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationValidationException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record CachePurgeProviderRequest(
        CacheInvalidationBatchId batchId,
        int attemptNumber,
        List<CachePurgeProviderTarget> targets) {
    public CachePurgeProviderRequest {
        Objects.requireNonNull(batchId, "batchId");
        targets = List.copyOf(Objects.requireNonNull(targets, "targets"));
        if (attemptNumber < 1 || attemptNumber > 3 || targets.isEmpty()) {
            throw new CacheInvalidationValidationException(
                    "provider request requires pending targets and an attempt number within the aggregate budget");
        }
        Set<CacheInvalidationTargetId> identities = new HashSet<>();
        for (CachePurgeProviderTarget target : targets) {
            if (target == null || !identities.add(target.targetId())) {
                throw new CacheInvalidationValidationException(
                        "provider request target identities must be unique");
            }
        }
    }
}
