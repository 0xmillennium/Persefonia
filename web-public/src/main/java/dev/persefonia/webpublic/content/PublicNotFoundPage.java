package dev.persefonia.webpublic.content;

import java.util.List;
import java.util.Objects;

public record PublicNotFoundPage(
        String title,
        String message,
        boolean noindex,
        List<String> stylesheetPaths) {
    public PublicNotFoundPage {
        title = Objects.requireNonNull(title, "title");
        message = Objects.requireNonNull(message, "message");
        stylesheetPaths = List.copyOf(Objects.requireNonNull(stylesheetPaths, "stylesheetPaths"));
    }
}
