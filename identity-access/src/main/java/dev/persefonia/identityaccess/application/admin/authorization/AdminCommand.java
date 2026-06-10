package dev.persefonia.identityaccess.application.admin.authorization;

import java.util.Objects;

public record AdminCommand(String name) {
    private static final int MAX_NAME_LENGTH = 128;

    public AdminCommand {
        name = Objects.requireNonNull(name, "name").trim();
        if (name.isBlank()) {
            throw new IllegalArgumentException("command name must not be blank");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("command name must be at most 128 characters");
        }
        if (name.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("command name must not contain control characters");
        }
    }

    public static AdminCommand named(String name) {
        return new AdminCommand(name);
    }
}
