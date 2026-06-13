package dev.persefonia.webpublic.content;

import java.util.Objects;

public record PublicNotFoundPage(
        String title,
        String message,
        boolean noindex) {
    public PublicNotFoundPage {
        title = Objects.requireNonNull(title, "title");
        message = Objects.requireNonNull(message, "message");
    }
}
