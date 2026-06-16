package dev.persefonia.contentpublishing.domain.model.series;

public record SeriesEntryPosition(int value) {
    public SeriesEntryPosition {
        if (value <= 0) {
            throw new SeriesValidationException("series entry position must be positive");
        }
    }

    public static SeriesEntryPosition of(int value) {
        return new SeriesEntryPosition(value);
    }
}
