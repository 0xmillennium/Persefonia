package dev.persefonia.webadmin.taxonomy;

import java.util.Objects;

public record AdminTagFieldError(String field, String message) {
    public AdminTagFieldError {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(message, "message");
    }
}
