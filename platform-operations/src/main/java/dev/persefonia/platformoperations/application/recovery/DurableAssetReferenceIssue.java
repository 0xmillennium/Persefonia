package dev.persefonia.platformoperations.application.recovery;

import java.util.Objects;
import java.util.UUID;

public record DurableAssetReferenceIssue(
        DurableAssetReferenceKind referenceKind,
        UUID sourceEntityId,
        UUID missingAssetId) {
    public DurableAssetReferenceIssue {
        Objects.requireNonNull(referenceKind, "referenceKind");
        Objects.requireNonNull(sourceEntityId, "sourceEntityId");
        Objects.requireNonNull(missingAssetId, "missingAssetId");
    }
}
