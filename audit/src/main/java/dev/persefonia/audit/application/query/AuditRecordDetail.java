package dev.persefonia.audit.application.query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Safe detail read model for a single audit record, including its ordered changes
 * and metadata. It exposes plain strings and immutable lists only; it never
 * exposes JDBC rows or mutable aggregate internals.
 */
public record AuditRecordDetail(
        UUID id,
        String action,
        String actorType,
        String actorContext,
        String actorSourceType,
        UUID actorId,
        String actorDisplay,
        String entityContext,
        String entityType,
        UUID entityId,
        String requestId,
        Instant occurredAt,
        Instant createdAt,
        List<AuditChangeView> changes,
        List<AuditMetadataView> metadata) {
    public AuditRecordDetail {
        changes = List.copyOf(changes);
        metadata = List.copyOf(metadata);
    }

    public Optional<String> requestIdOptional() {
        return Optional.ofNullable(requestId);
    }
}
