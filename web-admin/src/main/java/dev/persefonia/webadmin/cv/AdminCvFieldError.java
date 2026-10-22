package dev.persefonia.webadmin.cv;

public record AdminCvFieldError(String field, String message) implements dev.persefonia.webadmin.FieldError {
}
