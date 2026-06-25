package dev.persefonia.communication.domain.contact;

import java.util.regex.Pattern;

public record SenderEmail(String value) {
    public static final int MAX_LENGTH = 254;
    private static final Pattern PRAGMATIC_EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    public SenderEmail {
        value = ContactMessageValues.boundedText(value, "sender email", MAX_LENGTH, false).toLowerCase();
        if (!PRAGMATIC_EMAIL.matcher(value).matches()) {
            throw new ContactMessageValidationException("sender email must be valid");
        }
    }

    public static SenderEmail of(String value) {
        return new SenderEmail(value);
    }
}
