package dev.persefonia.contentpublishing.application.exception;

public final class TranslationGroupCommandRejectedException extends ContentApplicationException {
    public enum Reason {
        ALREADY_IN_GROUP,
        DUPLICATE_LANGUAGE,
        DIFFERENT_CONTENT_TYPE,
        ENTRY_NOT_FOUND,
        LAST_ENTRY
    }

    private final Reason reason;

    public TranslationGroupCommandRejectedException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
