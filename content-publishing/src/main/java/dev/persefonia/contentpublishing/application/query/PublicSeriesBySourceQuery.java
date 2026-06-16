package dev.persefonia.contentpublishing.application.query;

import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import java.util.Objects;
import java.util.UUID;

public record PublicSeriesBySourceQuery(
        UUID seriesId,
        ContentLanguage language,
        String expectedSlug) {
    public PublicSeriesBySourceQuery {
        Objects.requireNonNull(seriesId, "seriesId");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(expectedSlug, "expectedSlug");
        if (expectedSlug.isBlank()) {
            throw new IllegalArgumentException("expectedSlug must not be blank");
        }
    }
}
