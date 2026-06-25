package dev.persefonia.communication.application.port;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ContactMessageNotification(
        UUID contactMessageId,
        Instant submittedAt,
        String senderName,
        String senderEmail,
        String subject,
        String body) {
    public ContactMessageNotification {
        Objects.requireNonNull(contactMessageId, "contactMessageId must not be null");
        Objects.requireNonNull(submittedAt, "submittedAt must not be null");
        senderName = requireText(senderName, "senderName");
        senderEmail = requireText(senderEmail, "senderEmail");
        subject = requireText(subject, "subject");
        body = requireText(body, "body");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
