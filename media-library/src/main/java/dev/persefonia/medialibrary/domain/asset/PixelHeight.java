package dev.persefonia.medialibrary.domain.asset;

public record PixelHeight(int value) {
    public PixelHeight {
        AssetValues.positive(value, "pixel height");
    }

    public static PixelHeight of(int value) {
        return new PixelHeight(value);
    }
}
