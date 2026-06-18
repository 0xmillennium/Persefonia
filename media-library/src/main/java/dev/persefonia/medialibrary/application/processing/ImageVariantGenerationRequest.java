package dev.persefonia.medialibrary.application.processing;

import dev.persefonia.medialibrary.domain.asset.ContentTypeName;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public record ImageVariantGenerationRequest(
        byte[] originalBytes,
        ContentTypeName originalContentType,
        List<ImageVariantSpec> specs) {
    public ImageVariantGenerationRequest {
        Objects.requireNonNull(originalBytes, "originalBytes");
        Objects.requireNonNull(originalContentType, "originalContentType");
        originalBytes = Arrays.copyOf(originalBytes, originalBytes.length);
        specs = List.copyOf(Objects.requireNonNull(specs, "specs"));
    }

    @Override
    public byte[] originalBytes() {
        return Arrays.copyOf(originalBytes, originalBytes.length);
    }
}
