package dev.persefonia.communication.application.command;

import dev.persefonia.communication.domain.contact.ContactMessageId;
import dev.persefonia.communication.domain.contact.ContactMessageStatus;
import java.util.Objects;

public sealed interface UpdateContactMessageStatusResult
        permits UpdateContactMessageStatusResult.Updated,
                UpdateContactMessageStatusResult.NotFound,
                UpdateContactMessageStatusResult.Rejected {
    record Updated(ContactMessageId messageId, ContactMessageStatus status) implements UpdateContactMessageStatusResult {
        public Updated {
            Objects.requireNonNull(messageId, "messageId");
            Objects.requireNonNull(status, "status");
        }
    }

    record NotFound(ContactMessageId messageId) implements UpdateContactMessageStatusResult {
        public NotFound {
            Objects.requireNonNull(messageId, "messageId");
        }
    }

    record Rejected(String message) implements UpdateContactMessageStatusResult {
        public Rejected {
            Objects.requireNonNull(message, "message");
            if (message.isBlank()) {
                throw new IllegalArgumentException("message must not be blank");
            }
        }
    }
}
