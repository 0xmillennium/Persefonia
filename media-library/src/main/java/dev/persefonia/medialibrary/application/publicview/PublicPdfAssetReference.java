package dev.persefonia.medialibrary.application.publicview;

import dev.persefonia.medialibrary.domain.asset.AssetId;
import java.time.Instant;
import java.util.Objects;

public record PublicPdfAssetReference(
        AssetId assetId,
        String originalFilename,
        String contentType,
        long sizeBytes,
        Instant updatedAt) {
    public PublicPdfAssetReference {
        Objects.requireNonNull(assetId, "assetId");
        Objects.requireNonNull(originalFilename, "originalFilename");
        Objects.requireNonNull(contentType, "contentType");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
