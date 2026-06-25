package dev.persefonia.communication.domain.contact;

public record SafeFailureReason(String value) {
    public static final int MAX_LENGTH = 500;

    public SafeFailureReason {
        value = ContactMessageValues.boundedText(value, "failure reason", MAX_LENGTH, false);
    }

    public static SafeFailureReason of(String value) {
        return new SafeFailureReason(value);
    }
}
