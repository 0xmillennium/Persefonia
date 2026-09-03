package dev.persefonia.audit.application.service;

import dev.persefonia.audit.application.command.AppendAuditChangeCommand;
import dev.persefonia.audit.application.command.AppendAuditMetadataCommand;
import dev.persefonia.audit.application.command.AppendAuditRecordCommand;
import dev.persefonia.audit.domain.record.AuditAction;
import dev.persefonia.audit.domain.record.AuditActorRef;
import dev.persefonia.audit.domain.record.AuditValidationException;
import dev.persefonia.audit.domain.record.AuditedEntityRef;
import dev.persefonia.audit.domain.record.DisplayName;
import dev.persefonia.audit.domain.record.FieldPath;
import dev.persefonia.audit.domain.record.MetadataKey;
import dev.persefonia.audit.domain.record.RequestId;
import dev.persefonia.audit.domain.record.SafeAuditValue;
import dev.persefonia.audit.domain.record.SafeMetadataValue;
import dev.persefonia.audit.domain.record.SourceContext;
import dev.persefonia.audit.domain.record.SourceEntityId;
import dev.persefonia.audit.domain.record.SourceType;
import java.time.Instant;
import java.util.Objects;

/**
 * Validates the complete append command before it can reach the repository. It
 * delegates identifier, display, correlation, key, and persisted-value checks to
 * domain value objects and adds command-level actor-shape checks.
 *
 * <p>Rejection messages name the offending category only; they never echo the
 * rejected raw value.
 */
public final class AuditSafeValuePolicy {
    public void validate(AppendAuditRecordCommand command) {
        Objects.requireNonNull(command, "command");
        AuditAction.of(command.action());
        validateActor(command);
        AuditedEntityRef.of(command.entityContext(), command.entityType(), command.entityId());
        requireOccurredAt(command.occurredAt());
        if (command.requestId() != null) {
            RequestId.of(command.requestId());
        }
        for (AppendAuditChangeCommand change : command.changes()) {
            validateChange(change);
        }
        for (AppendAuditMetadataCommand entry : command.metadata()) {
            validateMetadata(entry);
        }
    }

    public FieldPath fieldPath(String value) {
        return FieldPath.of(value);
    }

    public MetadataKey metadataKey(String value) {
        return MetadataKey.of(value);
    }

    public SafeAuditValue auditValue(String value) {
        return SafeAuditValue.of(value);
    }

    public SafeMetadataValue metadataValue(String value) {
        return SafeMetadataValue.of(value);
    }

    private void validateActor(AppendAuditRecordCommand command) {
        switch (command.actorType()) {
            case ADMIN -> {
                if (command.actorContext() == null
                        || command.actorSourceType() == null
                        || command.actorId() == null
                        || command.actorDisplay() == null) {
                    throw new AuditValidationException(
                            "admin actor requires context, source type, id, and display");
                }
                AuditActorRef.admin(
                        SourceContext.of(command.actorContext()),
                        SourceType.of(command.actorSourceType()),
                        SourceEntityId.from(command.actorId()),
                        DisplayName.of(command.actorDisplay()));
            }
            case SYSTEM -> {
                if (command.actorContext() != null
                        || command.actorSourceType() != null
                        || command.actorId() != null) {
                    throw new AuditValidationException(
                            "system actor must not carry a source reference");
                }
                if (command.actorDisplay() == null) {
                    throw new AuditValidationException("system actor requires a display");
                }
                AuditActorRef.system(DisplayName.of(command.actorDisplay()));
            }
        }
    }

    private static void requireOccurredAt(Instant occurredAt) {
        if (occurredAt == null) {
            throw new AuditValidationException("audit occurrence time must not be null");
        }
    }

    private void validateChange(AppendAuditChangeCommand change) {
        Objects.requireNonNull(change, "change");
        fieldPath(change.fieldPath());
        if (change.oldValue() == null && change.newValue() == null) {
            throw new AuditValidationException("audit change requires an old value or a new value");
        }
        if (change.oldValue() != null) {
            auditValue(change.oldValue());
        }
        if (change.newValue() != null) {
            auditValue(change.newValue());
        }
    }

    private void validateMetadata(AppendAuditMetadataCommand entry) {
        Objects.requireNonNull(entry, "metadata entry");
        metadataKey(entry.key());
        metadataValue(entry.value());
    }
}
