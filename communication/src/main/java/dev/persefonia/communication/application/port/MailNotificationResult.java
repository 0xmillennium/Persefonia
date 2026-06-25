package dev.persefonia.communication.application.port;

import java.util.Objects;
import java.util.Optional;

public record MailNotificationResult(Status status, String failureReason) {
    public MailNotificationResult {
        Objects.requireNonNull(status, "status must not be null");
        if (status == Status.SENT && failureReason != null) {
            throw new IllegalArgumentException("sent mail notification cannot include a failure reason");
        }
        if (status == Status.FAILED) {
            failureReason = requireFailureReason(failureReason);
        }
    }

    public static MailNotificationResult sent() {
        return new MailNotificationResult(Status.SENT, null);
    }

    public static MailNotificationResult failed(String failureReason) {
        return new MailNotificationResult(Status.FAILED, failureReason);
    }

    public Optional<String> failureReasonValue() {
        return Optional.ofNullable(failureReason);
    }

    private static String requireFailureReason(String value) {
        Objects.requireNonNull(value, "failureReason must not be null for failed mail notification");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("failureReason must not be blank");
        }
        return normalized;
    }

    public enum Status {
        SENT,
        FAILED
    }
}
