package dev.persefonia.app.platformoperations.cache.execution;

import dev.persefonia.platformoperations.application.cache.CacheInvalidationExecutionPort;
import dev.persefonia.platformoperations.application.cache.CacheInvalidationRequest;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationBatchId;
import dev.persefonia.platformoperations.domain.cache.CachePurgeResult;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CachePurgeExecutionCoordinator implements CacheInvalidationExecutionPort {
    private static final Logger LOGGER = LoggerFactory.getLogger(CachePurgeExecutionCoordinator.class);

    private final CachePurgeTransactionService transactions;
    private final NonTransactionalCachePurgeInvoker providerInvoker;
    private final Clock clock;

    public CachePurgeExecutionCoordinator(
            CachePurgeTransactionService transactions,
            NonTransactionalCachePurgeInvoker providerInvoker,
            Clock clock) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.providerInvoker = Objects.requireNonNull(providerInvoker, "providerInvoker");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void requestAndExecute(CacheInvalidationRequest request) {
        execute(() -> transactions.createAndReserve(request), null);
    }

    @Override
    public void executeInitial(CacheInvalidationBatchId batchId) {
        execute(() -> transactions.reserveInitial(batchId), batchId);
    }

    @Override
    public void executeManualRetry(CacheInvalidationBatchId batchId) {
        execute(() -> transactions.reserveManualRetry(batchId), batchId);
    }

    private void execute(Supplier<CachePurgeWorkItem> reservation, CacheInvalidationBatchId requestedBatchId) {
        CachePurgeWorkItem workItem;
        try {
            workItem = reservation.get();
        } catch (RuntimeException operationalFailure) {
            LOGGER.warn("Cache purge reservation failed. batchId={} phase=reservation",
                    safeId(requestedBatchId));
            return;
        }

        Instant attemptedAt;
        CachePurgeInvocationResult invocation;
        Instant recordedAt;
        try {
            attemptedAt = clock.instant();
            invocation = providerInvoker.invoke(workItem);
            recordedAt = clock.instant();
        } catch (RuntimeException operationalFailure) {
            LOGGER.warn("Cache purge provider invocation could not complete. batchId={} attemptNumber={} phase=provider",
                    workItem.batchId().value(), workItem.attemptNumber());
            return;
        }
        if (invocation.result().result() == CachePurgeResult.FAILED) {
            LOGGER.warn("Cache purge provider failed. batchId={} attemptNumber={} provider={} failureReason={}",
                    workItem.batchId().value(), workItem.attemptNumber(), invocation.provider(),
                    invocation.result().failureReason());
        }
        try {
            transactions.recordResult(workItem, invocation.provider(), invocation.result(), attemptedAt, recordedAt);
        } catch (RuntimeException operationalFailure) {
            LOGGER.warn("Cache purge result persistence failed. batchId={} attemptNumber={} phase=result_persistence",
                    workItem.batchId().value(), workItem.attemptNumber());
        }
    }

    private static Object safeId(CacheInvalidationBatchId batchId) {
        return batchId == null ? "unassigned" : batchId.value();
    }
}
