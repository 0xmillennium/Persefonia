package dev.persefonia.communication.application.command;

import dev.persefonia.communication.application.port.ContactMessageRepository;
import dev.persefonia.communication.domain.contact.ContactBody;
import dev.persefonia.communication.domain.contact.ContactMessage;
import dev.persefonia.communication.domain.contact.ContactMessageId;
import dev.persefonia.communication.domain.contact.ContactMessageValidationException;
import dev.persefonia.communication.domain.contact.ContactSubject;
import dev.persefonia.communication.domain.contact.SenderEmail;
import dev.persefonia.communication.domain.contact.SenderName;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public final class SubmitContactMessageCommandService {
    private final ContactMessageRepository messages;

    public SubmitContactMessageCommandService(ContactMessageRepository messages) {
        this.messages = Objects.requireNonNull(messages, "messages must not be null");
    }

    public SubmitContactMessageResult submit(SubmitContactMessageCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        SenderName senderName = validate("senderName", () -> SenderName.of(command.senderName()), fieldErrors);
        SenderEmail senderEmail = validate("senderEmail", () -> SenderEmail.of(command.senderEmail()), fieldErrors);
        ContactSubject subject = validate("subject", () -> ContactSubject.of(command.subject()), fieldErrors);
        ContactBody body = validate("body", () -> ContactBody.of(command.body()), fieldErrors);

        if (!fieldErrors.isEmpty()) {
            return SubmitContactMessageResult.invalid(fieldErrors);
        }

        ContactMessage message = ContactMessage.create(
                ContactMessageId.newId(),
                senderName,
                senderEmail,
                subject,
                body,
                command.submittedAt());
        messages.save(message);
        return SubmitContactMessageResult.success(message.id());
    }

    private static <T> T validate(String field, Supplier<T> factory, Map<String, String> fieldErrors) {
        try {
            return factory.get();
        } catch (ContactMessageValidationException | NullPointerException exception) {
            fieldErrors.put(field, safeMessage(field, exception));
            return null;
        }
    }

    private static String safeMessage(String field, RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return field + " is invalid";
        }
        return message;
    }
}
