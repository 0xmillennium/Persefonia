package dev.persefonia.app.communication.mail;

import dev.persefonia.communication.application.port.ContactMessageNotification;
import java.util.Objects;

public final class ContactMailNotificationContentBuilder {
    static final int MAX_SUBJECT_LENGTH = 180;

    ContactMailNotificationContent build(
            ContactMessageNotification notification,
            ContactMailNotificationProperties properties) {
        Objects.requireNonNull(notification, "notification must not be null");
        Objects.requireNonNull(properties, "properties must not be null");
        return new ContactMailNotificationContent(
                subject(notification, properties.subjectPrefix()),
                body(notification));
    }

    String subject(ContactMessageNotification notification, String subjectPrefix) {
        String prefix = sanitizeSubjectPart(subjectPrefix == null ? "" : subjectPrefix);
        String submittedSubject = sanitizeSubjectPart(notification.subject());
        return truncate((prefix + " " + submittedSubject).trim(), MAX_SUBJECT_LENGTH);
    }

    String body(ContactMessageNotification notification) {
        return """
                Contact message id: %s
                Submitted at: %s
                Sender name: %s
                Sender email: %s
                Subject: %s

                Body:
                %s
                """.formatted(
                notification.contactMessageId(),
                notification.submittedAt(),
                notification.senderName(),
                notification.senderEmail(),
                notification.subject(),
                notification.body());
    }

    private static String sanitizeSubjectPart(String value) {
        StringBuilder sanitized = new StringBuilder(value.length());
        boolean previousWhitespace = false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isISOControl(character)) {
                if (!previousWhitespace) {
                    sanitized.append(' ');
                    previousWhitespace = true;
                }
            } else {
                sanitized.append(character);
                previousWhitespace = Character.isWhitespace(character);
            }
        }
        return sanitized.toString().trim().replaceAll("\\s+", " ");
    }

    private static String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength).trim();
    }
}
