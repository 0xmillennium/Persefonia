package dev.persefonia.contentpublishing.domain.content;

public record ReadingTime(int minutes) {
    public ReadingTime {
        if (minutes < 1) {
            throw new ContentValidationException("reading time minutes must be at least 1");
        }
    }

    public static ReadingTime minutes(int minutes) {
        return new ReadingTime(minutes);
    }
}
