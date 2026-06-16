package dev.persefonia.contentpublishing.application.exception;

public final class SeriesCommandRejectedException extends ContentApplicationException {
    public enum Reason {
        DUPLICATE_SLUG,
        DUPLICATE_ENTRY,
        LANGUAGE_MISMATCH,
        ARCHIVED_SERIES,
        ARCHIVED_CONTENT,
        ENTRY_NOT_FOUND,
        INVALID_REORDER
    }

    private final Reason reason;

    public SeriesCommandRejectedException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
