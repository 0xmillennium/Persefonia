package dev.persefonia.webadmin;

import java.util.Objects;

public record LogoutFormViewModel(
        String action,
        String csrfParameterName,
        String csrfToken) {
    public LogoutFormViewModel {
        requireNonBlank(action, "action");
        requireNonBlank(csrfParameterName, "csrfParameterName");
        requireNonBlank(csrfToken, "csrfToken");
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
