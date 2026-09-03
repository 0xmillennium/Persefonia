package dev.persefonia.audit.application;

import dev.persefonia.audit.application.command.AppendAuditChangeCommand;
import dev.persefonia.audit.application.command.AppendAuditMetadataCommand;
import dev.persefonia.audit.application.command.AppendAuditRecordCommand;
import dev.persefonia.audit.domain.record.AuditActorType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class AuditCommands {
    static final Instant OCCURRED_AT = Instant.parse("2026-06-25T10:00:00Z");

    private AuditCommands() {
    }

    static AppendAuditRecordCommand safeAdminCommand() {
        return new AppendAuditRecordCommand(
                "content.published",
                AuditActorType.ADMIN,
                "iam",
                "admin_account",
                UUID.randomUUID(),
                "Jane Admin",
                "publishing",
                "content_item",
                UUID.randomUUID(),
                "req-12ab34cd",
                OCCURRED_AT,
                List.of(
                        new AppendAuditChangeCommand("status", "DRAFT", "PUBLISHED"),
                        new AppendAuditChangeCommand("title", null, "New title")),
                List.of(new AppendAuditMetadataCommand("reason", "scheduled release")));
    }

    static AppendAuditRecordCommand safeSystemCommand() {
        return new AppendAuditRecordCommand(
                "content.unpublished",
                AuditActorType.SYSTEM,
                null,
                null,
                null,
                "System",
                "publishing",
                "content_item",
                UUID.randomUUID(),
                null,
                OCCURRED_AT,
                List.of(new AppendAuditChangeCommand("status", "PUBLISHED", "DRAFT")),
                List.of());
    }

    static AppendAuditRecordCommand contactStatusChangedCommand() {
        return new AppendAuditRecordCommand(
                "contact_message.status.changed",
                AuditActorType.ADMIN,
                "iam",
                "admin_account",
                UUID.randomUUID(),
                "Jane Admin",
                "communication",
                "contact_message",
                UUID.randomUUID(),
                "req-contact-status-1",
                OCCURRED_AT,
                List.of(new AppendAuditChangeCommand("status", "NEW", "READ")),
                List.of(new AppendAuditMetadataCommand("reason", "owner_review")));
    }

    static AppendAuditRecordCommand unsafeValueCommand() {
        String unsafe = "password=hunter2";
        return new AppendAuditRecordCommand(
                "content.published",
                AuditActorType.SYSTEM,
                null,
                null,
                null,
                "System",
                "publishing",
                "content_item",
                UUID.randomUUID(),
                null,
                OCCURRED_AT,
                List.of(new AppendAuditChangeCommand("title", null, unsafe)),
                List.of());
    }

    static AppendAuditRecordCommand unsafeKeyCommand() {
        return new AppendAuditRecordCommand(
                "contact_message.status.changed",
                AuditActorType.ADMIN,
                "iam",
                "admin_account",
                UUID.randomUUID(),
                "Jane Admin",
                "communication",
                "contact_message",
                UUID.randomUUID(),
                null,
                OCCURRED_AT,
                List.of(new AppendAuditChangeCommand("sender_email", null, "changed")),
                List.of());
    }

    static AppendAuditRecordCommand rawIpValueCommand() {
        return new AppendAuditRecordCommand(
                "contact_message.status.changed",
                AuditActorType.SYSTEM,
                null,
                null,
                null,
                "System",
                "communication",
                "contact_message",
                UUID.randomUUID(),
                null,
                OCCURRED_AT,
                List.of(new AppendAuditChangeCommand("status", "NEW", "8.8.8.8")),
                List.of());
    }
}
