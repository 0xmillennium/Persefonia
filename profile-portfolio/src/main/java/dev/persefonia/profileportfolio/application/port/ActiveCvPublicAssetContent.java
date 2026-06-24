package dev.persefonia.profileportfolio.application.port;

import java.io.InputStream;
import java.time.Instant;
import java.util.Objects;

public record ActiveCvPublicAssetContent(
        InputStream inputStream,
        String contentType,
        long sizeBytes,
        Instant updatedAt) {
    public ActiveCvPublicAssetContent {
        Objects.requireNonNull(inputStream, "inputStream");
        Objects.requireNonNull(contentType, "contentType");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("sizeBytes must be positive");
        }
    }
}
