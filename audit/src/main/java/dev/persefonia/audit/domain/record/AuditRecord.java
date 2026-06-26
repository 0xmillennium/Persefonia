package dev.persefonia.audit.domain.record;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Append-only audit aggregate root. An audit record captures who did what to
 * which entity, with an ordered list of safe field changes and an ordered list of
 * safe metadata entries. It owns its changes and metadata; there is no business
 * update, delete, archive, or replace behaviour, and there is no separate
 * repository for its children.
 */
public final class AuditRecord {
    private final AuditRecordId id;
    private final AuditAction action;
    private final AuditActorRef actor;
    private final AuditedEntityRef entity;
    private final RequestId requestId;
    private final Instant occurredAt;
    private final Instant createdAt;
    private final List<AuditChange> changes;
    private final List<AuditMetadataEntry> metadata;

    private AuditRecord(
            AuditRecordId id,
            AuditAction action,
            AuditActorRef actor,
            AuditedEntityRef entity,
            RequestId requestId,
            Instant occurredAt,
            Instant createdAt,
            List<AuditChange> changes,
            List<AuditMetadataEntry> metadata) {
        this.id = Objects.requireNonNull(id, "id");
        this.action = Objects.requireNonNull(action, "action");
        this.actor = Objects.requireNonNull(actor, "actor");
        this.entity = Objects.requireNonNull(entity, "entity");
        this.requestId = requestId;
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.changes = copyChanges(changes);
        this.metadata = copyMetadata(metadata);
    }

    public static AuditRecord create(
            AuditRecordId id,
            AuditAction action,
            AuditActorRef actor,
            AuditedEntityRef entity,
            RequestId requestId,
            Instant occurredAt,
            Instant createdAt,
            List<AuditChange> changes,
            List<AuditMetadataEntry> metadata) {
        return new AuditRecord(id, action, actor, entity, requestId, occurredAt, createdAt, changes, metadata);
    }

    public static AuditRecord rehydrate(
            AuditRecordId id,
            AuditAction action,
            AuditActorRef actor,
            AuditedEntityRef entity,
            RequestId requestId,
            Instant occurredAt,
            Instant createdAt,
            List<AuditChange> changes,
            List<AuditMetadataEntry> metadata) {
        return new AuditRecord(id, action, actor, entity, requestId, occurredAt, createdAt, changes, metadata);
    }

    private static List<AuditChange> copyChanges(List<AuditChange> changes) {
        Objects.requireNonNull(changes, "changes");
        Set<String> seen = new HashSet<>();
        List<AuditChange> copy = new ArrayList<>(changes.size());
        for (AuditChange change : changes) {
            Objects.requireNonNull(change, "change");
            if (!seen.add(change.fieldPath().value())) {
                throw new AuditValidationException("audit record must not contain duplicate change field paths");
            }
            copy.add(change);
        }
        return List.copyOf(copy);
    }

    private static List<AuditMetadataEntry> copyMetadata(List<AuditMetadataEntry> metadata) {
        Objects.requireNonNull(metadata, "metadata");
        Set<String> seen = new HashSet<>();
        List<AuditMetadataEntry> copy = new ArrayList<>(metadata.size());
        for (AuditMetadataEntry entry : metadata) {
            Objects.requireNonNull(entry, "metadata entry");
            if (!seen.add(entry.key().value())) {
                throw new AuditValidationException("audit record must not contain duplicate metadata keys");
            }
            copy.add(entry);
        }
        return List.copyOf(copy);
    }

    public AuditRecordId id() {
        return id;
    }

    public AuditAction action() {
        return action;
    }

    public AuditActorRef actor() {
        return actor;
    }

    public AuditedEntityRef entity() {
        return entity;
    }

    public Optional<RequestId> requestId() {
        return Optional.ofNullable(requestId);
    }

    public Instant occurredAt() {
        return occurredAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public List<AuditChange> changes() {
        return changes;
    }

    public List<AuditMetadataEntry> metadata() {
        return metadata;
    }
}
