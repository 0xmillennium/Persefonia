package dev.persefonia.webadmin.settings;

public record AdminSiteSettingsFieldError(String field, String message) implements dev.persefonia.webadmin.FieldError {
}
