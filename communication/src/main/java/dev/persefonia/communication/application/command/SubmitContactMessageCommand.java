package dev.persefonia.communication.application.command;

import java.time.Instant;
import java.util.Objects;

public record SubmitContactMessageCommand(
        String senderName,
        String senderEmail,
        String subject,
        String body,
        Instant submittedAt) {
    public SubmitContactMessageCommand {
        Objects.requireNonNull(submittedAt, "submittedAt must not be null");
    }
}
