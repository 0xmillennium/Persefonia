package dev.persefonia.profileportfolio.application.exception;

public class PersonalProfileApplicationException extends RuntimeException {
    public PersonalProfileApplicationException(String message) {
        super(message);
    }

    public PersonalProfileApplicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
