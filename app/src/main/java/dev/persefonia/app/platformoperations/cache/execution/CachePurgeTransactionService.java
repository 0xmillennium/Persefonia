package dev.persefonia.app.platformoperations.cache.execution;

import dev.persefonia.platformoperations.application.cache.CacheInvalidationRequest;
import dev.persefonia.platformoperations.application.cache.CacheInvalidationRequestPort;
import dev.persefonia.platformoperations.application.cache.CachePurgeProviderResult;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationBatch;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationBatchId;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationBatchRepository;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationValidationException;
import dev.persefonia.platformoperations.domain.cache.CachePurgeProvider;
import dev.persefonia.platformoperations.application.operations.CacheInvalidationRecoveryPolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CachePurgeTransactionService {
    private final CacheInvalidationRequestPort requests;
    private final CacheInvalidationBatchRepository batches;
    private final Clock clock;
    private final CacheInvalidationRecoveryPolicy recoveryPolicy;

    public CachePurgeTransactionService(
            CacheInvalidationRequestPort requests,
            CacheInvalidationBatchRepository batches,
            Clock clock,
            CacheInvalidationRecoveryPolicy recoveryPolicy) {
        this.requests = Objects.requireNonNull(requests, "requests");
        this.batches = Objects.requireNonNull(batches, "batches");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.recoveryPolicy = Objects.requireNonNull(recoveryPolicy, "recoveryPolicy");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CachePurgeWorkItem createAndReserve(CacheInvalidationRequest request) {
        CacheInvalidationBatchId batchId = requests.request(Objects.requireNonNull(request, "request"));
        CacheInvalidationBatch batch = required(batchId);
        batch.beginInitialAttempt(clock.instant());
        batches.save(batch);
        return CachePurgeWorkItem.from(batch);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CachePurgeWorkItem reserveInitial(CacheInvalidationBatchId batchId) {
        CacheInvalidationBatch batch = required(batchId);
        batch.beginInitialAttempt(clock.instant());
        batches.save(batch);
        return CachePurgeWorkItem.from(batch);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CachePurgeWorkItem reserveManualRetry(CacheInvalidationBatchId batchId) {
        CacheInvalidationBatch batch = required(batchId);
        batch.beginManualRetry(clock.instant());
        batches.save(batch);
        return CachePurgeWorkItem.from(batch);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CachePurgeWorkItem reserveStrandedReplay(CacheInvalidationBatchId batchId) {
        CacheInvalidationBatch batch = required(batchId);
        Instant now = clock.instant();
        batch.beginStrandedReplay(now, recoveryPolicy.strandedCutoff(now));
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
        if (batch.version() != workItem.reservationVersion()) {
            throw new CacheInvalidationValidationException("cache purge result belongs to a stale reservation");
        }
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
