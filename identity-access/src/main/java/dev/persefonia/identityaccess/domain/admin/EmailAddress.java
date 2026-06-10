package dev.persefonia.identityaccess.domain.admin;

import java.util.Objects;

public record EmailAddress(String value) {
    private static final int MAX_LENGTH = 320;

    public EmailAddress {
        Objects.requireNonNull(value, "value");
        if (value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("value must not contain control characters");
        }
        value = value.trim();
        validate(value);
    }

    public static EmailAddress of(String value) {
        return new EmailAddress(value);
    }

    static void validate(String value) {
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("value must not exceed " + MAX_LENGTH + " characters");
        }
        if (value.chars().anyMatch(character -> Character.isWhitespace(character)
                || Character.isISOControl(character))) {
            throw new IllegalArgumentException("value must not contain whitespace or control characters");
        }

        int atIndex = value.indexOf('@');
        if (atIndex < 0 || atIndex != value.lastIndexOf('@')) {
            throw new IllegalArgumentException("value must contain exactly one @");
        }
        if (atIndex == 0) {
            throw new IllegalArgumentException("local part must not be blank");
        }

        String domain = value.substring(atIndex + 1);
        if (domain.isBlank()) {
            throw new IllegalArgumentException("domain part must not be blank");
        }
        if (!domain.contains(".")) {
            throw new IllegalArgumentException("domain part must contain a dot");
        }
    }
}
