package dev.persefonia.medialibrary.application.recovery;

import dev.persefonia.medialibrary.domain.asset.AssetId;
import java.util.Objects;

public record MediaRecoveryIssue(
        MediaRecoveryIssueCategory category,
        MediaRecoveryObjectKind objectKind,
        AssetId assetId,
        String variantName) {
    public MediaRecoveryIssue {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(objectKind, "objectKind");
        Objects.requireNonNull(assetId, "assetId");
    }
}
