package dev.persefonia.profileportfolio.application.exception;

public final class ProjectCommandRejectedException extends ProjectApplicationException {
    public enum Reason {
        DUPLICATE_SLUG,
        MISSING_TAG,
        ARCHIVED_TAG
    }

    private final Reason reason;

    public ProjectCommandRejectedException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
