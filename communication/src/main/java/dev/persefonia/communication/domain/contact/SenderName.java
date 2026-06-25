package dev.persefonia.communication.domain.contact;

public record SenderName(String value) {
    public static final int MAX_LENGTH = 120;

    public SenderName {
        value = ContactMessageValues.boundedText(value, "sender name", MAX_LENGTH, false);
    }

    public static SenderName of(String value) {
        return new SenderName(value);
    }
}
