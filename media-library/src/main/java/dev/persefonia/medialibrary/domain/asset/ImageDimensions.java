package dev.persefonia.medialibrary.domain.asset;

import java.util.Objects;

public record ImageDimensions(PixelWidth width, PixelHeight height) {
    public ImageDimensions {
        Objects.requireNonNull(width, "width");
        Objects.requireNonNull(height, "height");
    }

    public static ImageDimensions of(int width, int height) {
        return new ImageDimensions(PixelWidth.of(width), PixelHeight.of(height));
    }
}
