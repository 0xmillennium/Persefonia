package dev.persefonia.communication.application.command;

import dev.persefonia.communication.application.authorization.ContactMessageCommandAuthorizationPolicy;
import dev.persefonia.communication.application.port.ContactMessageRepository;
import dev.persefonia.communication.domain.contact.ContactMessageStatus;
import dev.persefonia.communication.domain.contact.ContactMessageStatusChangeId;
import dev.persefonia.communication.domain.contact.ContactMessageValidationException;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public final class UpdateContactMessageStatusCommandService {
    private static final String COMMAND_NAME = "communication.contact-message.update-status";
    private static final Set<ContactMessageStatus> ALLOWED_TARGET_STATUSES = EnumSet.of(
            ContactMessageStatus.READ,
            ContactMessageStatus.REPLIED,
            ContactMessageStatus.SPAM,
            ContactMessageStatus.ARCHIVED);

    private final ContactMessageRepository messages;
    private final ContactMessageCommandAuthorizationPolicy authorization;

    public UpdateContactMessageStatusCommandService(
            ContactMessageRepository messages,
            ContactMessageCommandAuthorizationPolicy authorization) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
    }

    public UpdateContactMessageStatusResult update(UpdateContactMessageStatusCommand command) {
        Objects.requireNonNull(command, "command");
        authorization.requireOwner(command.actor(), COMMAND_NAME);

        if (!ALLOWED_TARGET_STATUSES.contains(command.newStatus())) {
            return new UpdateContactMessageStatusResult.Rejected("Unsupported contact message status action.");
        }

        return messages.findById(command.messageId())
                .<UpdateContactMessageStatusResult>map(message -> {
                    try {
                        message.changeStatus(
                                ContactMessageStatusChangeId.newId(),
                                command.newStatus(),
                                command.changedBy(),
                                command.changedAt());
                    } catch (ContactMessageValidationException exception) {
                        return new UpdateContactMessageStatusResult.Rejected(safeMessage(exception));
                    }
                    messages.save(message);
                    return new UpdateContactMessageStatusResult.Updated(message.id(), message.status());
                })
                .orElseGet(() -> new UpdateContactMessageStatusResult.NotFound(command.messageId()));
    }

    private static String safeMessage(ContactMessageValidationException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "Contact message status update was rejected." : message;
    }
}
