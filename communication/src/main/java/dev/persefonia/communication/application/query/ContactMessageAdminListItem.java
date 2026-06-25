package dev.persefonia.communication.application.query;

import dev.persefonia.communication.domain.contact.ContactMessageId;
import dev.persefonia.communication.domain.contact.ContactMessageStatus;
import dev.persefonia.communication.domain.contact.MailDeliveryStatus;
import dev.persefonia.communication.domain.contact.MailNotificationAttemptResult;
import java.time.Instant;
import java.util.Optional;

public record ContactMessageAdminListItem(
        ContactMessageId id,
        String senderName,
        String senderEmail,
        String subject,
        ContactMessageStatus status,
        MailDeliveryStatus mailDeliveryStatus,
        Instant submittedAt,
        Instant updatedAt,
        long mailAttemptCount,
        MailNotificationAttemptResult latestMailAttemptResult) {
    public Optional<MailNotificationAttemptResult> latestMailAttemptResultOptional() {
        return Optional.ofNullable(latestMailAttemptResult);
    }
}
