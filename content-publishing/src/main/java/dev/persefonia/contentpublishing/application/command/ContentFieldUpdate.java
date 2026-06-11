package dev.persefonia.contentpublishing.application.command;

public record ContentFieldUpdate<T>(boolean specified, T value) {
    public static <T> ContentFieldUpdate<T> unchanged() {
        return new ContentFieldUpdate<>(false, null);
    }

    public static <T> ContentFieldUpdate<T> set(T value) {
        if (value == null) {
            throw new IllegalArgumentException("updated value must not be null");
        }
        return new ContentFieldUpdate<>(true, value);
    }

    public static <T> ContentFieldUpdate<T> clear() {
        return new ContentFieldUpdate<>(true, null);
    }
}
