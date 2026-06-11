package dev.persefonia.contentpublishing.domain.content;

import java.util.Objects;
import java.util.UUID;

public record AssetId(UUID value) {
    public AssetId {
        Objects.requireNonNull(value, "value");
    }

    public static AssetId newId() {
        return new AssetId(UUID.randomUUID());
    }

    public static AssetId from(UUID value) {
        return new AssetId(value);
    }
}
