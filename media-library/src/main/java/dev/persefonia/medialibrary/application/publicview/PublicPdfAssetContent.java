package dev.persefonia.medialibrary.application.publicview;

import java.io.InputStream;
import java.time.Instant;
import java.util.Objects;

public record PublicPdfAssetContent(
        InputStream inputStream,
        String contentType,
        long contentLength,
        Instant updatedAt) {
    public PublicPdfAssetContent {
        Objects.requireNonNull(inputStream, "inputStream");
        Objects.requireNonNull(contentType, "contentType");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (contentLength <= 0) {
            throw new IllegalArgumentException("contentLength must be positive");
        }
    }
}
