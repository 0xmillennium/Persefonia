package dev.persefonia.communication.domain.contact;

import java.util.Objects;
import java.util.UUID;

public record MailNotificationAttemptId(UUID value) {
    public MailNotificationAttemptId {
        Objects.requireNonNull(value, "value");
    }

    public static MailNotificationAttemptId from(UUID value) {
        return new MailNotificationAttemptId(value);
    }

    public static MailNotificationAttemptId newId() {
        return new MailNotificationAttemptId(UUID.randomUUID());
    }
}
