package dev.persefonia.webadmin;

import java.util.List;
import java.util.stream.Collectors;

public final class FieldErrors {
    private FieldErrors() {}

    public static boolean has(List<? extends FieldError> errors, String field) {
        return errors.stream().anyMatch(error -> error.field().equals(field));
    }

    public static List<String> fields(List<? extends FieldError> errors) {
        return errors.stream().map(FieldError::field).distinct().toList();
    }

    public static String message(List<? extends FieldError> errors, String field) {
        return errors.stream().filter(error -> error.field().equals(field))
                .map(FieldError::message).collect(Collectors.joining(" "));
    }
}
