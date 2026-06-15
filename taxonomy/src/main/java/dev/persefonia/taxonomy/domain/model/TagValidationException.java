package dev.persefonia.taxonomy.domain.model;

public final class TagValidationException extends IllegalArgumentException {
    public TagValidationException(String message) {
        super(message);
    }
}
