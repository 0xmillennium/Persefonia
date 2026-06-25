package dev.persefonia.communication.domain.contact;

public record ContactBody(String value) {
    public static final int MAX_LENGTH = 5000;

    public ContactBody {
        value = ContactMessageValues.boundedText(value, "contact body", MAX_LENGTH, true);
    }

    public static ContactBody of(String value) {
        return new ContactBody(value);
    }
}
