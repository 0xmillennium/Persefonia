package dev.persefonia.platformoperations.application.operations;

import dev.persefonia.platformoperations.domain.cache.CacheInvalidationBatchId;
import java.time.Instant;
import java.util.Optional;

public interface CacheInvalidationOperationsReadPort {
    CacheInvalidationOperationsListPage search(CacheInvalidationOperationsSearchRequest request, Instant now);
    Optional<CacheInvalidationOperationsDetail> findById(CacheInvalidationBatchId id, Instant now);
    CacheInvalidationOperationsSummary summarize(Instant now);
}
