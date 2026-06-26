package dev.persefonia.audit.application.service;

import dev.persefonia.audit.application.command.AppendAuditChangeCommand;
import dev.persefonia.audit.application.command.AppendAuditMetadataCommand;
import dev.persefonia.audit.application.command.AppendAuditRecordCommand;
import dev.persefonia.audit.domain.record.AuditValidationException;
import dev.persefonia.audit.domain.record.FieldPath;
import dev.persefonia.audit.domain.record.MetadataKey;
import dev.persefonia.audit.domain.record.SafeAuditValue;
import dev.persefonia.audit.domain.record.SafeMetadataValue;
import java.util.Objects;

/**
 * Validates that an append command and every field path, metadata key, audit
 * value, and metadata value it carries is privacy-safe before it can reach the
 * repository. It delegates per-value rules to the domain value objects, which are
 * the single source of truth, and adds command-level actor-shape checks.
 *
 * <p>Rejection messages name the offending category only; they never echo the
 * rejected raw value.
 */
public final class AuditSafeValuePolicy {
    public void validate(AppendAuditRecordCommand command) {
        Objects.requireNonNull(command, "command");
        validateActorShape(command);
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

    private void validateActorShape(AppendAuditRecordCommand command) {
        switch (command.actorType()) {
            case ADMIN -> {
                if (command.actorContext() == null
                        || command.actorSourceType() == null
                        || command.actorId() == null
                        || command.actorDisplay() == null) {
                    throw new AuditValidationException(
                            "admin actor requires context, source type, id, and display");
                }
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
            }
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
