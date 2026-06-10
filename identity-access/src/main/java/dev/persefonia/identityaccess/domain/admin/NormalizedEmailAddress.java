package dev.persefonia.identityaccess.domain.admin;

import java.util.Locale;
import java.util.Objects;

public record NormalizedEmailAddress(String value) {
    public NormalizedEmailAddress {
        Objects.requireNonNull(value, "value");
        if (value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("value must not contain control characters");
        }
        value = value.trim().toLowerCase(Locale.ROOT);
        EmailAddress.validate(value);
    }

    public static NormalizedEmailAddress from(EmailAddress email) {
        Objects.requireNonNull(email, "email");
        return new NormalizedEmailAddress(email.value());
    }

    public static NormalizedEmailAddress of(String value) {
        return new NormalizedEmailAddress(value);
    }
}
