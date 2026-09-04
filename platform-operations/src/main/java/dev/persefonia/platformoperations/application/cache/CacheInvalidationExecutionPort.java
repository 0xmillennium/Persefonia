package dev.persefonia.platformoperations.application.cache;

import dev.persefonia.platformoperations.domain.cache.CacheInvalidationBatchId;

public interface CacheInvalidationExecutionPort {
    void requestAndExecute(CacheInvalidationRequest request);
    void executeInitial(CacheInvalidationBatchId batchId);
    void executeManualRetry(CacheInvalidationBatchId batchId);
    void resumeStranded(CacheInvalidationBatchId batchId);
}
