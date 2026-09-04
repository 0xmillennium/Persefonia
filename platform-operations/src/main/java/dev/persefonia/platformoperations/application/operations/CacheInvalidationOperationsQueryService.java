package dev.persefonia.platformoperations.application.operations;

import dev.persefonia.platformoperations.domain.cache.CacheInvalidationBatchId;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

public final class CacheInvalidationOperationsQueryService implements CacheInvalidationOperationsQueryPort {
    private final CacheInvalidationOperationsReadPort reads;
    private final Clock clock;

    public CacheInvalidationOperationsQueryService(CacheInvalidationOperationsReadPort reads, Clock clock) {
        this.reads = Objects.requireNonNull(reads, "reads");
        this.clock = Objects.requireNonNull(clock, "clock");
    }
    @Override public CacheInvalidationOperationsListPage search(CacheInvalidationOperationsSearchRequest request) {
        return reads.search(Objects.requireNonNull(request, "request"), clock.instant());
    }
    @Override public Optional<CacheInvalidationOperationsDetail> findById(CacheInvalidationBatchId id) {
        return reads.findById(Objects.requireNonNull(id, "id"), clock.instant());
    }
    @Override public CacheInvalidationOperationsSummary summarize() { return reads.summarize(clock.instant()); }
}
