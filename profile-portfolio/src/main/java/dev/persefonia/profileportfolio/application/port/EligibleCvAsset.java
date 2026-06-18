package dev.persefonia.profileportfolio.application.port;

import dev.persefonia.profileportfolio.domain.cv.MediaAssetId;
import java.time.Instant;
import java.util.Objects;

public record EligibleCvAsset(
        MediaAssetId mediaAssetId,
        String originalFilename,
        String contentType,
        long sizeBytes,
        Instant updatedAt) {
    public EligibleCvAsset {
        Objects.requireNonNull(mediaAssetId, "mediaAssetId");
        Objects.requireNonNull(originalFilename, "originalFilename");
        Objects.requireNonNull(contentType, "contentType");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
