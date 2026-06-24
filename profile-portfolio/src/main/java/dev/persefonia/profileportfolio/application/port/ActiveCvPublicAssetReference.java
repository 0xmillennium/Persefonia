package dev.persefonia.profileportfolio.application.port;

import dev.persefonia.profileportfolio.domain.cv.MediaAssetId;
import java.time.Instant;
import java.util.Objects;

public record ActiveCvPublicAssetReference(
        MediaAssetId mediaAssetId,
        String contentType,
        long sizeBytes,
        Instant updatedAt) {
    public ActiveCvPublicAssetReference {
        Objects.requireNonNull(mediaAssetId, "mediaAssetId");
        Objects.requireNonNull(contentType, "contentType");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("sizeBytes must be positive");
        }
    }
}
