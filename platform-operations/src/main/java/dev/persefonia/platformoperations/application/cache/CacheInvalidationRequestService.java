package dev.persefonia.platformoperations.application.cache;

import dev.persefonia.platformoperations.domain.cache.CacheInvalidationBatch;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationBatchId;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationBatchRepository;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationTarget;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationTargetId;
import dev.persefonia.platformoperations.domain.cache.CacheTargetValue;
import java.time.Clock;
import java.util.Objects;

public final class CacheInvalidationRequestService implements CacheInvalidationRequestPort {
    private final CacheInvalidationBatchRepository batches;
    private final Clock clock;

    public CacheInvalidationRequestService(CacheInvalidationBatchRepository batches, Clock clock) {
        this.batches = Objects.requireNonNull(batches, "batches");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public CacheInvalidationBatchId request(CacheInvalidationRequest request) {
        Objects.requireNonNull(request, "request");
        var targets = request.targets().stream()
                .map(target -> CacheInvalidationTarget.pending(
                        CacheInvalidationTargetId.newId(),
                        target.targetType(),
                        CacheTargetValue.of(target.targetType(), target.value())))
                .toList();
        CacheInvalidationBatch batch = CacheInvalidationBatch.request(
                CacheInvalidationBatchId.newId(), request.reason(), request.requestedBy(), clock.instant(), targets);
        batches.save(batch);
        return batch.id();
    }
}
