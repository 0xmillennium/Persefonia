package dev.persefonia.contentpublishing.domain.translation;

import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import java.time.Instant;
import java.util.Objects;

public record TranslationGroupEntry(
        TranslationGroupEntryId id,
        ContentId contentItemId,
        ContentLanguage language,
        ContentType contentType,
        Instant addedAt) {
    public TranslationGroupEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(contentItemId, "contentItemId");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(contentType, "contentType");
        Objects.requireNonNull(addedAt, "addedAt");
    }
}
