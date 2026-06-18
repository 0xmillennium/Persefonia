package dev.persefonia.medialibrary.domain.asset;

public record PixelWidth(int value) {
    public PixelWidth {
        AssetValues.positive(value, "pixel width");
    }

    public static PixelWidth of(int value) {
        return new PixelWidth(value);
    }
}
