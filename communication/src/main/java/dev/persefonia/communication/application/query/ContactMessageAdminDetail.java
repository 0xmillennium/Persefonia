package dev.persefonia.communication.application.query;

import dev.persefonia.communication.domain.contact.ContactMessageId;
import dev.persefonia.communication.domain.contact.ContactMessageStatus;
import dev.persefonia.communication.domain.contact.MailDeliveryStatus;
import java.time.Instant;
import java.util.List;

public record ContactMessageAdminDetail(
        ContactMessageId id,
        String senderName,
        String senderEmail,
        String subject,
        String body,
        ContactMessageStatus status,
        MailDeliveryStatus mailDeliveryStatus,
        Instant submittedAt,
        Instant updatedAt,
        List<ContactMessageAdminMailAttemptItem> mailAttempts,
        List<ContactMessageAdminStatusChangeItem> statusChanges) {
    public ContactMessageAdminDetail {
        mailAttempts = List.copyOf(mailAttempts);
        statusChanges = List.copyOf(statusChanges);
    }
}
