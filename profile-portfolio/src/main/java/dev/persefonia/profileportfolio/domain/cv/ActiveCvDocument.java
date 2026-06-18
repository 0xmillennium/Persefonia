package dev.persefonia.profileportfolio.domain.cv;

import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import java.time.Instant;
import java.util.Objects;

public record ActiveCvDocument(
        ActiveCvDocumentId id,
        ContentLanguage language,
        MediaAssetId mediaAssetId,
        CvDisplayLabel displayLabel,
        Instant selectedAt,
        Instant createdAt,
        Instant updatedAt) {
    public ActiveCvDocument {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(mediaAssetId, "mediaAssetId");
        Objects.requireNonNull(selectedAt, "selectedAt");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static ActiveCvDocument select(
            ContentLanguage language,
            MediaAssetId mediaAssetId,
            CvDisplayLabel displayLabel,
            Instant now) {
        return new ActiveCvDocument(
                ActiveCvDocumentId.newId(),
                language,
                mediaAssetId,
                displayLabel,
                now,
                now,
                now);
    }

    public ActiveCvDocument replace(MediaAssetId mediaAssetId, CvDisplayLabel displayLabel, Instant now) {
        return new ActiveCvDocument(id, language, mediaAssetId, displayLabel, now, createdAt, now);
    }

}
