package dev.persefonia.contentpublishing.domain.content;

public record HeadingLevel(int value) {
    public HeadingLevel {
        if (value < 1 || value > 6) {
            throw new ContentValidationException("heading level must be between 1 and 6");
        }
    }

    public static HeadingLevel of(int value) {
        return new HeadingLevel(value);
    }
}
