package dev.persefonia.medialibrary.application.processing;

import dev.persefonia.medialibrary.domain.asset.ImageDimensions;
import java.util.Objects;

public record ImageMetadata(ImageDimensions dimensions) {
    public ImageMetadata {
        Objects.requireNonNull(dimensions, "dimensions");
    }
}
