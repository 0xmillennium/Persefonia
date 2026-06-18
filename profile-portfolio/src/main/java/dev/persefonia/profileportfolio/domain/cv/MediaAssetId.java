package dev.persefonia.profileportfolio.domain.cv;

import java.util.Objects;
import java.util.UUID;

public record MediaAssetId(UUID value) {
    public MediaAssetId {
        Objects.requireNonNull(value, "value");
    }

    public static MediaAssetId from(UUID value) {
        return new MediaAssetId(value);
    }
}
