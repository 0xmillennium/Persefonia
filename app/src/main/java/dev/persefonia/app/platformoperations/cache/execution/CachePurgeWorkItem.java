package dev.persefonia.app.platformoperations.cache.execution;

import dev.persefonia.platformoperations.application.cache.CachePurgeProviderRequest;
import dev.persefonia.platformoperations.application.cache.CachePurgeProviderTarget;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationBatch;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationBatchId;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationValidationException;
import dev.persefonia.platformoperations.domain.cache.CacheTargetStatus;
import java.util.List;
import java.util.Objects;

public record CachePurgeWorkItem(
        CacheInvalidationBatchId batchId,
        int attemptNumber,
        long reservationVersion,
        List<CachePurgeProviderTarget> pendingTargets) {
    public CachePurgeWorkItem {
        Objects.requireNonNull(batchId, "batchId");
        pendingTargets = List.copyOf(Objects.requireNonNull(pendingTargets, "pendingTargets"));
        if (attemptNumber < 1 || attemptNumber > 3 || reservationVersion < 1 || pendingTargets.isEmpty()) {
            throw new CacheInvalidationValidationException("reserved purge work item is invalid");
        }
    }

    static CachePurgeWorkItem from(CacheInvalidationBatch batch) {
        List<CachePurgeProviderTarget> targets = batch.targets().stream()
                .filter(target -> target.status() == CacheTargetStatus.PENDING)
                .map(target -> new CachePurgeProviderTarget(target.id(), target.targetType(), target.value()))
                .toList();
        return new CachePurgeWorkItem(batch.id(), batch.attempts().size() + 1, batch.version(), targets);
    }

    CachePurgeProviderRequest providerRequest() {
        return new CachePurgeProviderRequest(batchId, attemptNumber, pendingTargets);
    }
}
