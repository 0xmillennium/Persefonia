package dev.persefonia.platformoperations.application.recovery;

import java.util.Objects;
import java.util.UUID;

public record RecoveryMediaIssue(
        RecoveryMediaIssueCategory category,
        RecoveryMediaObjectKind objectKind,
        UUID assetId,
        String variantName) {
    public RecoveryMediaIssue {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(objectKind, "objectKind");
        Objects.requireNonNull(assetId, "assetId");
    }
}
