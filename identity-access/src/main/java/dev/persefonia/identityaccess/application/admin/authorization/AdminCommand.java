package dev.persefonia.identityaccess.application.admin.authorization;

import java.util.Objects;
import java.util.regex.Pattern;

public record AdminCommand(String name) {
    private static final int MAX_NAME_LENGTH = 128;
    private static final Pattern VALID_NAME = Pattern.compile("[a-z0-9][a-z0-9._:-]{0,127}");

    public AdminCommand {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("command name must not be blank");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("command name must be at most 128 characters");
        }
        if (name.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("command name must not contain control characters");
        }
        if (!VALID_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("command name must match [a-z0-9][a-z0-9._:-]{0,127}");
        }
    }

    public static AdminCommand named(String name) {
        return new AdminCommand(name);
    }
}
