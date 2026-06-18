package dev.persefonia.medialibrary.domain.asset;

import java.util.Objects;
import java.util.UUID;

public record AssetVariantId(UUID value) {
    public AssetVariantId {
        Objects.requireNonNull(value, "value");
    }

    public static AssetVariantId from(UUID value) {
        return new AssetVariantId(value);
    }

    public static AssetVariantId newId() {
        return new AssetVariantId(UUID.randomUUID());
    }
}
