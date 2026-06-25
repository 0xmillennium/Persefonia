package dev.persefonia.communication.domain.contact;

import java.time.Instant;
import java.util.Objects;

public record ContactMessageStatusChange(
        ContactMessageStatusChangeId id,
        ContactMessageStatus previousStatus,
        ContactMessageStatus newStatus,
        AdminAccountId changedBy,
        Instant changedAt) {
    public ContactMessageStatusChange {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(previousStatus, "previousStatus");
        Objects.requireNonNull(newStatus, "newStatus");
        Objects.requireNonNull(changedBy, "changedBy");
        Objects.requireNonNull(changedAt, "changedAt");
        if (previousStatus == newStatus) {
            throw new ContactMessageValidationException("status change must change status");
        }
    }
}
