package dev.persefonia.profileportfolio.application.exception;

public class ProjectApplicationException extends RuntimeException {
    public ProjectApplicationException(String message) {
        super(message);
    }

    public ProjectApplicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
