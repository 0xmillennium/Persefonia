package dev.persefonia.contentpublishing.domain.content;

public class ContentValidationException extends IllegalArgumentException {
    public ContentValidationException(String message) {
        super(message);
    }
}
