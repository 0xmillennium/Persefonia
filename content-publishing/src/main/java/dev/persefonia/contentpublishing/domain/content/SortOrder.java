package dev.persefonia.contentpublishing.domain.content;

public record SortOrder(int value) {
    public SortOrder {
        if (value < 0) {
            throw new ContentValidationException("sort order must not be negative");
        }
    }

    public static SortOrder of(int value) {
        return new SortOrder(value);
    }
}
