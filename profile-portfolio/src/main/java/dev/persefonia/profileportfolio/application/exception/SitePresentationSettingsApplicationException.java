package dev.persefonia.profileportfolio.application.exception;

public class SitePresentationSettingsApplicationException extends RuntimeException {
    public SitePresentationSettingsApplicationException(String message) {
        super(message);
    }

    public SitePresentationSettingsApplicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
