package dev.persefonia.webadmin.profile;

public record AdminPersonalProfileFieldError(String field, String message) implements dev.persefonia.webadmin.FieldError {
}
