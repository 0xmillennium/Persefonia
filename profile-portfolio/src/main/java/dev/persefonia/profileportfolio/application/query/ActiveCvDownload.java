package dev.persefonia.profileportfolio.application.query;

import java.io.InputStream;
import java.time.Instant;
import java.util.Objects;

public record ActiveCvDownload(
        String language,
        String filename,
        InputStream inputStream,
        String contentType,
        long contentLength,
        Instant assetUpdatedAt) {
    public ActiveCvDownload {
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(filename, "filename");
        Objects.requireNonNull(inputStream, "inputStream");
        Objects.requireNonNull(contentType, "contentType");
        Objects.requireNonNull(assetUpdatedAt, "assetUpdatedAt");
        if (contentLength <= 0) {
            throw new IllegalArgumentException("contentLength must be positive");
        }
    }
}
