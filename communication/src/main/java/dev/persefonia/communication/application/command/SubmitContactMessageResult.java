package dev.persefonia.communication.application.command;

import dev.persefonia.communication.application.port.ContactMessageNotification;
import dev.persefonia.communication.domain.contact.ContactMessageId;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record SubmitContactMessageResult(
        ContactMessageId messageId,
        ContactMessageNotification notification,
        Map<String, String> fieldErrors) {
    public SubmitContactMessageResult {
        fieldErrors = Map.copyOf(Objects.requireNonNull(fieldErrors, "fieldErrors must not be null"));
        if (messageId != null && !fieldErrors.isEmpty()) {
            throw new IllegalArgumentException("successful contact submission cannot include field errors");
        }
        if (messageId != null && notification == null) {
            throw new IllegalArgumentException("successful contact submission must include notification snapshot");
        }
        if (messageId == null && notification != null) {
            throw new IllegalArgumentException("failed contact submission cannot include notification snapshot");
        }
        if (messageId == null && fieldErrors.isEmpty()) {
            throw new IllegalArgumentException("failed contact submission must include field errors");
        }
    }

    public static SubmitContactMessageResult success(
            ContactMessageId messageId,
            ContactMessageNotification notification) {
        return new SubmitContactMessageResult(
                Objects.requireNonNull(messageId, "messageId must not be null"),
                Objects.requireNonNull(notification, "notification must not be null"),
                Map.of());
    }

    public static SubmitContactMessageResult invalid(Map<String, String> fieldErrors) {
        return new SubmitContactMessageResult(null, null, fieldErrors);
    }

    public boolean successful() {
        return messageId != null;
    }

    public Optional<ContactMessageId> messageIdValue() {
        return Optional.ofNullable(messageId);
    }

    public Optional<ContactMessageNotification> notificationValue() {
        return Optional.ofNullable(notification);
    }
}
