package dev.persefonia.audit.application.command;

import dev.persefonia.audit.domain.record.AuditActorType;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Application boundary command for appending an audit record. It carries only
 * safe, typed reference data and safe field/metadata text. It intentionally
 * carries no source aggregate objects, repositories, transport-level request
 * objects, or raw request targets.
 */
public record AppendAuditRecordCommand(
        String action,
        AuditActorType actorType,
        String actorContext,
        String actorSourceType,
        UUID actorId,
        String actorDisplay,
        String entityContext,
        String entityType,
        UUID entityId,
        String requestId,
        Instant occurredAt,
        List<AppendAuditChangeCommand> changes,
        List<AppendAuditMetadataCommand> metadata) {
    public AppendAuditRecordCommand {
        actorType = Objects.requireNonNull(actorType, "actorType");
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        changes = List.copyOf(Objects.requireNonNull(changes, "changes"));
        metadata = List.copyOf(Objects.requireNonNull(metadata, "metadata"));
    }
}
