package dev.persefonia.taxonomy.application.exception;

public final class TagCommandRejectedException extends TaxonomyApplicationException {
    public enum Reason {
        DUPLICATE_SLUG,
        DUPLICATE_NORMALIZED_NAME
    }

    private final Reason reason;

    public TagCommandRejectedException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
