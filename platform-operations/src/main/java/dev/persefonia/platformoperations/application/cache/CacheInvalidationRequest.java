package dev.persefonia.platformoperations.application.cache;

import dev.persefonia.platformoperations.domain.cache.InvalidationReason;
import dev.persefonia.platformoperations.domain.cache.InvalidationRequester;
import java.util.List;
import java.util.Objects;

public record CacheInvalidationRequest(
        InvalidationReason reason,
        InvalidationRequester requestedBy,
        List<CacheInvalidationTargetRequest> targets) {
    public CacheInvalidationRequest {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(requestedBy, "requestedBy");
        targets = List.copyOf(Objects.requireNonNull(targets, "targets"));
    }
}
