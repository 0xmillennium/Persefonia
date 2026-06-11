package dev.persefonia.contentpublishing.domain.content;

public record SortOrder(int value) {
    public SortOrder {
        if (value < 1) {
            throw new ContentValidationException("sort order must be positive");
        }
    }

    public static SortOrder of(int value) {
        return new SortOrder(value);
    }
}
