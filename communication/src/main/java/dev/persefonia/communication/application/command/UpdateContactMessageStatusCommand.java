package dev.persefonia.communication.application.command;

import dev.persefonia.communication.application.authorization.ContactMessageCommandActor;
import dev.persefonia.communication.domain.contact.AdminAccountId;
import dev.persefonia.communication.domain.contact.ContactMessageId;
import dev.persefonia.communication.domain.contact.ContactMessageStatus;
import java.time.Instant;
import java.util.Objects;

public record UpdateContactMessageStatusCommand(
        ContactMessageCommandActor actor,
        ContactMessageId messageId,
        ContactMessageStatus newStatus,
        AdminAccountId changedBy,
        Instant changedAt) {
    public UpdateContactMessageStatusCommand {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(newStatus, "newStatus");
        Objects.requireNonNull(changedBy, "changedBy");
        Objects.requireNonNull(changedAt, "changedAt");
    }
}
