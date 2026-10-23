package dev.persefonia.webadmin.media;

import java.util.Objects;

public record AdminMediaFieldError(String field, String message) implements dev.persefonia.webadmin.FieldError {
    public AdminMediaFieldError {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(message, "message");
    }
}
