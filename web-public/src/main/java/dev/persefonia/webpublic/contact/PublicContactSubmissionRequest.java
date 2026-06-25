package dev.persefonia.webpublic.contact;

import java.util.Objects;

public record PublicContactSubmissionRequest(
        String senderName,
        String senderEmail,
        String subject,
        String body,
        String transientClientSignal) {
    public PublicContactSubmissionRequest {
        Objects.requireNonNull(transientClientSignal, "transientClientSignal must not be null");
    }
}
