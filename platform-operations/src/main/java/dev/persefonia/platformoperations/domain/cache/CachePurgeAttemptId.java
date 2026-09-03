package dev.persefonia.platformoperations.domain.cache;

import java.util.Objects;
import java.util.UUID;

public record CachePurgeAttemptId(UUID value) {
    public CachePurgeAttemptId { Objects.requireNonNull(value, "value"); }
    public static CachePurgeAttemptId newId() { return new CachePurgeAttemptId(UUID.randomUUID()); }
    public static CachePurgeAttemptId from(UUID value) { return new CachePurgeAttemptId(value); }
}
