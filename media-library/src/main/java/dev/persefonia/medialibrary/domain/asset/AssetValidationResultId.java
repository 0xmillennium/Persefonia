package dev.persefonia.medialibrary.domain.asset;

import java.util.Objects;
import java.util.UUID;

public record AssetValidationResultId(UUID value) {
    public AssetValidationResultId {
        Objects.requireNonNull(value, "value");
    }

    public static AssetValidationResultId from(UUID value) {
        return new AssetValidationResultId(value);
    }

    public static AssetValidationResultId newId() {
        return new AssetValidationResultId(UUID.randomUUID());
    }
}
