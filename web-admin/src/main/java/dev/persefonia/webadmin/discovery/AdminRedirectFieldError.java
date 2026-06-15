package dev.persefonia.webadmin.discovery;

import java.util.Objects;

public record AdminRedirectFieldError(String field, String message) {
    public AdminRedirectFieldError {
        requireNonBlank(field, "field");
        requireNonBlank(message, "message");
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
