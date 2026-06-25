package dev.persefonia.communication.domain.contact;

public record ContactSubject(String value) {
    public static final int MAX_LENGTH = 160;

    public ContactSubject {
        value = ContactMessageValues.boundedText(value, "contact subject", MAX_LENGTH, false);
    }

    public static ContactSubject of(String value) {
        return new ContactSubject(value);
    }
}
