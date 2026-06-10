package dev.persefonia.identityaccess.domain.admin;

import java.util.Objects;

public record DisplayName(String value) {
    private static final int MAX_LENGTH = 200;

    public DisplayName {
        Objects.requireNonNull(value, "value");
        if (value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("value must not contain control characters");
        }
        value = value.trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("value must not exceed " + MAX_LENGTH + " characters");
        }
    }

    public static DisplayName of(String value) {
        return new DisplayName(value);
    }
}
