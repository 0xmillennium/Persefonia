package dev.persefonia.app.platformoperations.cache.execution;

import dev.persefonia.platformoperations.application.cache.CacheInvalidationRequest;
import dev.persefonia.platformoperations.application.cache.CacheInvalidationRequestPort;
import dev.persefonia.platformoperations.application.cache.CachePurgeProviderResult;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationBatch;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationBatchId;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationBatchRepository;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationValidationException;
import dev.persefonia.platformoperations.domain.cache.CachePurgeProvider;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CachePurgeTransactionService {
    private final CacheInvalidationRequestPort requests;
    private final CacheInvalidationBatchRepository batches;

    public CachePurgeTransactionService(
            CacheInvalidationRequestPort requests, CacheInvalidationBatchRepository batches) {
        this.requests = Objects.requireNonNull(requests, "requests");
        this.batches = Objects.requireNonNull(batches, "batches");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CachePurgeWorkItem createAndReserve(CacheInvalidationRequest request) {
        CacheInvalidationBatchId batchId = requests.request(Objects.requireNonNull(request, "request"));
        CacheInvalidationBatch batch = required(batchId);
        batch.beginInitialAttempt();
        batches.save(batch);
        return CachePurgeWorkItem.from(batch);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CachePurgeWorkItem reserveInitial(CacheInvalidationBatchId batchId) {
        CacheInvalidationBatch batch = required(batchId);
        batch.beginInitialAttempt();
        batches.save(batch);
        return CachePurgeWorkItem.from(batch);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CachePurgeWorkItem reserveManualRetry(CacheInvalidationBatchId batchId) {
        CacheInvalidationBatch batch = required(batchId);
        batch.beginManualRetry();
        batches.save(batch);
        return CachePurgeWorkItem.from(batch);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordResult(
            CachePurgeWorkItem workItem,
            CachePurgeProvider provider,
            CachePurgeProviderResult result,
            Instant attemptedAt,
            Instant recordedAt) {
        Objects.requireNonNull(workItem, "workItem");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(result, "result");
        result.validateFor(workItem.providerRequest());
        CacheInvalidationBatch batch = required(workItem.batchId());
        batch.recordAttemptResult(workItem.attemptNumber(), provider, attemptedAt, result.result(),
                result.failureReason(), result.outcomes(), recordedAt);
        batches.save(batch);
    }

    private CacheInvalidationBatch required(CacheInvalidationBatchId batchId) {
        Objects.requireNonNull(batchId, "batchId");
        return batches.findById(batchId).orElseThrow(() ->
                new CacheInvalidationValidationException("cache invalidation batch does not exist"));
    }
}
