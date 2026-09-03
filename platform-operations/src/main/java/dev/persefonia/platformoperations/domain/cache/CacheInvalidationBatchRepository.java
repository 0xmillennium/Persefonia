package dev.persefonia.platformoperations.domain.cache;

import java.util.List;
import java.util.Optional;

public interface CacheInvalidationBatchRepository {
    void save(CacheInvalidationBatch batch);
    Optional<CacheInvalidationBatch> findById(CacheInvalidationBatchId id);
    List<CacheInvalidationBatch> findPendingBatches(int limit);
    List<CacheInvalidationBatch> findRecentFailures(int limit);
}
