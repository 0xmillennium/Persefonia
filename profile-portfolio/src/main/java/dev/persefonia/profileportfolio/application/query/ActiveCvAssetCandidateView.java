package dev.persefonia.profileportfolio.application.query;

import java.time.Instant;
import java.util.UUID;

public record ActiveCvAssetCandidateView(
        UUID mediaAssetId,
        String originalFilename,
        String contentType,
        long sizeBytes,
        Instant updatedAt) {
}
