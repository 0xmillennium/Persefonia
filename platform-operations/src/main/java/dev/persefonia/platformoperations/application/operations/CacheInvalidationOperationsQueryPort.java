package dev.persefonia.platformoperations.application.operations;

import dev.persefonia.platformoperations.domain.cache.CacheInvalidationBatchId;
import java.util.Optional;

public interface CacheInvalidationOperationsQueryPort {
    CacheInvalidationOperationsListPage search(CacheInvalidationOperationsSearchRequest request);
    Optional<CacheInvalidationOperationsDetail> findById(CacheInvalidationBatchId id);
    CacheInvalidationOperationsSummary summarize();
}
