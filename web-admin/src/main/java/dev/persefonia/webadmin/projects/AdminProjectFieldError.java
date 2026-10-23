package dev.persefonia.webadmin.projects;

public record AdminProjectFieldError(String field, String message) implements dev.persefonia.webadmin.FieldError {
}
