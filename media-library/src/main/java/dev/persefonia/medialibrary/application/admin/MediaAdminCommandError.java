package dev.persefonia.medialibrary.application.admin;

import java.util.Objects;

public record MediaAdminCommandError(String field, String message) {
    public MediaAdminCommandError {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(message, "message");
    }
}
