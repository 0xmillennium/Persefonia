package dev.persefonia.medialibrary.application.processing;

import dev.persefonia.medialibrary.domain.asset.ContentTypeName;
import dev.persefonia.medialibrary.domain.asset.FileExtension;
import dev.persefonia.medialibrary.domain.asset.PixelHeight;
import dev.persefonia.medialibrary.domain.asset.PixelWidth;
import dev.persefonia.medialibrary.domain.asset.VariantName;
import java.util.Arrays;
import java.util.Objects;

public record GeneratedImageVariant(
        VariantName name,
        PixelWidth width,
        PixelHeight height,
        ContentTypeName contentType,
        FileExtension fileExtension,
        byte[] bytes) {
    public GeneratedImageVariant {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(width, "width");
        Objects.requireNonNull(height, "height");
        Objects.requireNonNull(contentType, "contentType");
        Objects.requireNonNull(fileExtension, "fileExtension");
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length == 0) {
            throw new IllegalArgumentException("variant bytes must not be empty");
        }
        bytes = Arrays.copyOf(bytes, bytes.length);
    }

    @Override
    public byte[] bytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }
}
