package dev.persefonia.platformoperations.application.operations;

import dev.persefonia.platformoperations.domain.cache.CacheInvalidationBatchId;
import java.time.Instant;
import java.util.Objects;

public record CacheInvalidationRecoveryCommand(
        CacheOperationsCommandActor actor,
        CacheInvalidationBatchId batchId,
        Instant requestedAt) {
    public CacheInvalidationRecoveryCommand {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(batchId, "batchId");
        Objects.requireNonNull(requestedAt, "requestedAt");
    }
}
