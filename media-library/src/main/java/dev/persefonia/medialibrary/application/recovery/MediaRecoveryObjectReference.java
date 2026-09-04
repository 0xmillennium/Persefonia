package dev.persefonia.medialibrary.application.recovery;

import dev.persefonia.medialibrary.domain.asset.AssetId;
import dev.persefonia.medialibrary.domain.asset.StoragePath;
import java.util.Objects;
import java.util.UUID;

public record MediaRecoveryObjectReference(
        MediaRecoveryObjectKind kind,
        UUID objectId,
        AssetId assetId,
        String variantName,
        StoragePath storagePath,
        long expectedSize,
        String expectedChecksum) {
    public MediaRecoveryObjectReference {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(objectId, "objectId");
        Objects.requireNonNull(assetId, "assetId");
        Objects.requireNonNull(storagePath, "storagePath");
        Objects.requireNonNull(expectedChecksum, "expectedChecksum");
        if (expectedSize <= 0) throw new IllegalArgumentException("expected size must be positive");
        if (!expectedChecksum.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("expected checksum must be SHA-256");
        }
        if (kind == MediaRecoveryObjectKind.ORIGINAL && variantName != null) {
            throw new IllegalArgumentException("original cannot have a variant name");
        }
        if (kind == MediaRecoveryObjectKind.VARIANT && (variantName == null || variantName.isBlank())) {
            throw new IllegalArgumentException("variant name is required");
        }
    }
}
