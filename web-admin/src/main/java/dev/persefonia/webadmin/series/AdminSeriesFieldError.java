package dev.persefonia.webadmin.series;

import java.util.Objects;

public record AdminSeriesFieldError(String field, String message) implements dev.persefonia.webadmin.FieldError {
    public AdminSeriesFieldError {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(message, "message");
    }
}
