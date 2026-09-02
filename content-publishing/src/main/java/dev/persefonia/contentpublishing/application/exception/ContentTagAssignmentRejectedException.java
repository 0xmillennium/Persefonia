package dev.persefonia.contentpublishing.application.exception;

public final class ContentTagAssignmentRejectedException extends ContentApplicationException {
    public enum Reason {
        MISSING_TAG,
        ARCHIVED_TAG,
        TOO_MANY_TAGS,
        CONTENT_NOT_EDITABLE
    }

    private final Reason reason;

    public ContentTagAssignmentRejectedException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
