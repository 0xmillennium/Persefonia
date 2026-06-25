package dev.persefonia.communication.application.query;

import dev.persefonia.communication.domain.contact.AdminAccountId;
import dev.persefonia.communication.domain.contact.ContactMessageStatus;
import dev.persefonia.communication.domain.contact.ContactMessageStatusChangeId;
import java.time.Instant;

public record ContactMessageAdminStatusChangeItem(
        ContactMessageStatusChangeId id,
        ContactMessageStatus previousStatus,
        ContactMessageStatus newStatus,
        AdminAccountId changedBy,
        Instant changedAt) {
}
