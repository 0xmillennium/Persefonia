package dev.persefonia.platformoperations.application.cache;

import dev.persefonia.platformoperations.domain.cache.CacheInvalidationBatchId;

public interface CacheInvalidationRequestPort {
    CacheInvalidationBatchId request(CacheInvalidationRequest request);
}
