package dev.persefonia.medialibrary.domain.asset;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record AssetVariant(
        AssetVariantId id,
        VariantName name,
        PixelWidth width,
        PixelHeight height,
        ContentTypeName contentType,
        FileSize sizeBytes,
        StoragePath storagePath,
        PublicAssetUrl publicUrl,
        Checksum checksum,
        Instant createdAt) {
    public AssetVariant {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(width, "width");
        Objects.requireNonNull(height, "height");
        Objects.requireNonNull(contentType, "contentType");
        Objects.requireNonNull(sizeBytes, "sizeBytes");
        Objects.requireNonNull(storagePath, "storagePath");
        Objects.requireNonNull(checksum, "checksum");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public Optional<PublicAssetUrl> publicUrlOptional() {
        return Optional.ofNullable(publicUrl);
    }
}
