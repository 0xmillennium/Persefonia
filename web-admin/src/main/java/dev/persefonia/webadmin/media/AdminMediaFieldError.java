package dev.persefonia.webadmin.media;

import java.util.Objects;

public record AdminMediaFieldError(String field, String message) {
    public AdminMediaFieldError {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(message, "message");
    }
}
