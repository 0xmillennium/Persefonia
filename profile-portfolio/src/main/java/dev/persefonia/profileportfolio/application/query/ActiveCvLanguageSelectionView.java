package dev.persefonia.profileportfolio.application.query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public record ActiveCvLanguageSelectionView(
        String language,
        UUID mediaAssetId,
        String displayLabel,
        Instant selectedAt) {
    public Optional<UUID> mediaAssetIdValue() {
        return Optional.ofNullable(mediaAssetId);
    }
}
