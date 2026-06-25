package dev.persefonia.communication.application.query;

import dev.persefonia.communication.domain.contact.MailNotificationAttemptId;
import dev.persefonia.communication.domain.contact.MailNotificationAttemptResult;
import java.time.Instant;
import java.util.Optional;

public record ContactMessageAdminMailAttemptItem(
        MailNotificationAttemptId id,
        MailNotificationAttemptResult result,
        String failureReason,
        Instant attemptedAt) {
    public Optional<String> failureReasonOptional() {
        return Optional.ofNullable(failureReason);
    }
}
