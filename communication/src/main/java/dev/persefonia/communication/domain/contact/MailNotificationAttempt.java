package dev.persefonia.communication.domain.contact;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record MailNotificationAttempt(
        MailNotificationAttemptId id,
        MailNotificationAttemptResult result,
        SafeFailureReason failureReason,
        Instant attemptedAt) {
    public MailNotificationAttempt {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(attemptedAt, "attemptedAt");
        if (result == MailNotificationAttemptResult.SENT && failureReason != null) {
            throw new ContactMessageValidationException("sent mail attempt cannot include a failure reason");
        }
        if (result == MailNotificationAttemptResult.FAILED && failureReason == null) {
            throw new ContactMessageValidationException("failed mail attempt requires a failure reason");
        }
    }

    public static MailNotificationAttempt sent(MailNotificationAttemptId id, Instant attemptedAt) {
        return new MailNotificationAttempt(id, MailNotificationAttemptResult.SENT, null, attemptedAt);
    }

    public static MailNotificationAttempt failed(
            MailNotificationAttemptId id,
            SafeFailureReason failureReason,
            Instant attemptedAt) {
        return new MailNotificationAttempt(id, MailNotificationAttemptResult.FAILED, failureReason, attemptedAt);
    }

    public Optional<SafeFailureReason> failureReasonOptional() {
        return Optional.ofNullable(failureReason);
    }
}
