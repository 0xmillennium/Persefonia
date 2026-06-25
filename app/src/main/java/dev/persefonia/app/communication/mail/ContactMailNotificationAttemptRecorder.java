package dev.persefonia.app.communication.mail;

import dev.persefonia.communication.application.port.ContactMessageRepository;
import dev.persefonia.communication.application.port.MailNotificationResult;
import dev.persefonia.communication.domain.contact.ContactMessageId;
import dev.persefonia.communication.domain.contact.MailNotificationAttemptId;
import dev.persefonia.communication.domain.contact.SafeFailureReason;
import java.time.Clock;
import java.util.Locale;
import java.util.Objects;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class ContactMailNotificationAttemptRecorder {
    private static final String UNEXPECTED_MAIL_FAILURE = "unexpected_mail_failure";

    private final ContactMessageRepository messages;
    private final Clock clock;

    public ContactMailNotificationAttemptRecorder(ContactMessageRepository messages, Clock clock) {
        this.messages = Objects.requireNonNull(messages, "messages must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(ContactMessageId messageId, MailNotificationResult result) {
        Objects.requireNonNull(messageId, "messageId must not be null");
        Objects.requireNonNull(result, "result must not be null");

        messages.findById(messageId).ifPresent(message -> {
            var attemptId = MailNotificationAttemptId.newId();
            var attemptedAt = clock.instant();
            if (result.status() == MailNotificationResult.Status.SENT) {
                message.recordMailSent(attemptId, attemptedAt);
            } else {
                message.recordMailFailed(
                        attemptId,
                        SafeFailureReason.of(safeFailureReason(result.failureReason())),
                        attemptedAt);
            }
            messages.save(message);
        });
    }

    private static String safeFailureReason(String reason) {
        if (reason == null) {
            return UNEXPECTED_MAIL_FAILURE;
        }
        String normalized = reason.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (normalized.isEmpty() || normalized.length() > SafeFailureReason.MAX_LENGTH) {
            return UNEXPECTED_MAIL_FAILURE;
        }
        for (int index = 0; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            boolean safe = character == '_' || (character >= 'a' && character <= 'z') || (character >= '0' && character <= '9');
            if (!safe) {
                return UNEXPECTED_MAIL_FAILURE;
            }
        }
        return normalized;
    }
}
