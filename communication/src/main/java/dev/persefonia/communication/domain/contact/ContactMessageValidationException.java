package dev.persefonia.communication.domain.contact;

public class ContactMessageValidationException extends IllegalArgumentException {
    public ContactMessageValidationException(String message) {
        super(message);
    }
}
