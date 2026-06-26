package dev.persefonia.audit.application.query;

import java.time.Instant;
import java.util.UUID;

/**
 * Safe summary read model for an audit record, used by recent listings. It
 * exposes safe typed references and plain strings only.
 */
public record AuditRecordListItem(
        UUID id,
        String action,
        String actorType,
        String actorDisplay,
        String entityContext,
        String entityType,
        UUID entityId,
        Instant occurredAt) {
}
