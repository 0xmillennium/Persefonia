package dev.persefonia.audit.application.service;

import dev.persefonia.audit.application.command.AppendAuditChangeCommand;
import dev.persefonia.audit.application.command.AppendAuditMetadataCommand;
import dev.persefonia.audit.application.command.AppendAuditRecordCommand;
import dev.persefonia.audit.domain.record.AuditAction;
import dev.persefonia.audit.domain.record.AuditActorRef;
import dev.persefonia.audit.domain.record.AuditChange;
import dev.persefonia.audit.domain.record.AuditMetadataEntry;
import dev.persefonia.audit.domain.record.AuditRecord;
import dev.persefonia.audit.domain.record.AuditRecordId;
import dev.persefonia.audit.domain.record.AuditedEntityRef;
import dev.persefonia.audit.domain.record.DisplayName;
import dev.persefonia.audit.domain.record.RequestId;
import dev.persefonia.audit.domain.record.SafeAuditValue;
import dev.persefonia.audit.domain.record.SourceContext;
import dev.persefonia.audit.domain.record.SourceEntityId;
import dev.persefonia.audit.domain.record.SourceType;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Converts a validated {@link AppendAuditRecordCommand} into an {@link AuditRecord}
 * aggregate. It applies the {@link AuditSafeValuePolicy} before constructing any
 * domain value object, preserves command order for changes and metadata, and
 * relies on domain invariants to reject duplicate field paths and metadata keys.
 * It never touches infrastructure.
 */
public final class AuditRecordFactory {
    private final AuditSafeValuePolicy policy;
    private final Clock clock;

    public AuditRecordFactory(AuditSafeValuePolicy policy, Clock clock) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public AuditRecord create(AppendAuditRecordCommand command) {
        Objects.requireNonNull(command, "command");
        policy.validate(command);
        return AuditRecord.create(
                AuditRecordId.newId(),
                AuditAction.of(command.action()),
                actor(command),
                AuditedEntityRef.of(command.entityContext(), command.entityType(), command.entityId()),
                command.requestId() == null ? null : RequestId.of(command.requestId()),
                command.occurredAt(),
                clock.instant(),
                changes(command),
                metadata(command));
    }

    private AuditActorRef actor(AppendAuditRecordCommand command) {
        return switch (command.actorType()) {
            case ADMIN -> AuditActorRef.admin(
                    SourceContext.of(command.actorContext()),
                    SourceType.of(command.actorSourceType()),
                    SourceEntityId.from(command.actorId()),
                    DisplayName.of(command.actorDisplay()));
            case SYSTEM -> AuditActorRef.system(DisplayName.of(command.actorDisplay()));
        };
    }

    private List<AuditChange> changes(AppendAuditRecordCommand command) {
        List<AuditChange> changes = new ArrayList<>(command.changes().size());
        for (AppendAuditChangeCommand change : command.changes()) {
            changes.add(new AuditChange(
                    policy.fieldPath(change.fieldPath()),
                    change.oldValue() == null ? null : SafeAuditValue.of(change.oldValue()),
                    change.newValue() == null ? null : SafeAuditValue.of(change.newValue())));
        }
        return changes;
    }

    private List<AuditMetadataEntry> metadata(AppendAuditRecordCommand command) {
        List<AuditMetadataEntry> metadata = new ArrayList<>(command.metadata().size());
        for (AppendAuditMetadataCommand entry : command.metadata()) {
            metadata.add(new AuditMetadataEntry(
                    policy.metadataKey(entry.key()),
                    policy.metadataValue(entry.value())));
        }
        return metadata;
    }
}
