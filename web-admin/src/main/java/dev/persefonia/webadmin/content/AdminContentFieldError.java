package dev.persefonia.webadmin.content;

import java.util.Objects;

public record AdminContentFieldError(String field, String message) implements dev.persefonia.webadmin.FieldError {
    public AdminContentFieldError {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(message, "message");
    }
}
