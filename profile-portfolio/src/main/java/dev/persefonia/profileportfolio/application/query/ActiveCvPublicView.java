package dev.persefonia.profileportfolio.application.query;

import java.time.Instant;
import java.util.Objects;

public record ActiveCvPublicView(
        String language,
        String displayLabel,
        String downloadPath,
        String displayFilename,
        String contentType,
        long sizeBytes,
        Instant selectedAt,
        Instant assetUpdatedAt) {
    public ActiveCvPublicView {
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(displayLabel, "displayLabel");
        Objects.requireNonNull(downloadPath, "downloadPath");
        Objects.requireNonNull(displayFilename, "displayFilename");
        Objects.requireNonNull(contentType, "contentType");
        Objects.requireNonNull(selectedAt, "selectedAt");
        Objects.requireNonNull(assetUpdatedAt, "assetUpdatedAt");
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("sizeBytes must be positive");
        }
    }
}
