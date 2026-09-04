package dev.persefonia.platformoperations.application.recovery;

import java.time.Instant;
import java.util.Objects;

public record RecoveryVerificationReport(
        RecoveryVerificationContext context,
        RecoveryVerificationStatus status,
        Instant generatedAt,
        RecoveryMediaIntegritySummary media,
        DurableAssetReferenceIntegritySummary assetReferences) {
    public RecoveryVerificationReport {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(generatedAt, "generatedAt");
        Objects.requireNonNull(media, "media");
        Objects.requireNonNull(assetReferences, "assetReferences");
    }
}
