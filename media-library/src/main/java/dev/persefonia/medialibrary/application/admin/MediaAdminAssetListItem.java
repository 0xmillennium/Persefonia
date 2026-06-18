package dev.persefonia.medialibrary.application.admin;

import dev.persefonia.medialibrary.domain.asset.AssetId;
import dev.persefonia.medialibrary.domain.asset.AssetKind;
import dev.persefonia.medialibrary.domain.asset.AssetVisibility;
import dev.persefonia.medialibrary.domain.asset.ProcessingStatus;
import java.time.Instant;
import java.util.Objects;

public record MediaAdminAssetListItem(
        AssetId assetId,
        String originalFilename,
        AssetKind kind,
        AssetVisibility visibility,
        ProcessingStatus processingStatus,
        String contentType,
        String fileExtension,
        long sizeBytes,
        String checksum,
        Integer imageWidth,
        Integer imageHeight,
        Instant createdAt,
        Instant updatedAt) {
    public MediaAdminAssetListItem {
        Objects.requireNonNull(assetId, "assetId");
        Objects.requireNonNull(originalFilename, "originalFilename");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(processingStatus, "processingStatus");
        Objects.requireNonNull(contentType, "contentType");
        Objects.requireNonNull(fileExtension, "fileExtension");
        Objects.requireNonNull(checksum, "checksum");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public String shortChecksum() {
        return checksum.length() <= 12 ? checksum : checksum.substring(0, 12);
    }

    public String dimensionsLabel() {
        return imageWidth == null || imageHeight == null ? "" : imageWidth + " x " + imageHeight;
    }
}
