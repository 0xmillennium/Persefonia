package dev.persefonia.discovery.application.index;

import dev.persefonia.discovery.application.contract.DiscoveryLanguage;
import java.time.Instant;
import java.util.Objects;

public record PublicFeedEntry(
        String sourceType,
        String sourceId,
        DiscoveryLanguage language,
        String publicUrl,
        String canonicalUrl,
        String title,
        String summary,
        Instant publishedAt,
        Instant updatedAt) {
    public PublicFeedEntry {
        requireNonBlank(sourceType, "sourceType");
        requireNonBlank(sourceId, "sourceId");
        Objects.requireNonNull(language, "language");
        requireNonBlank(publicUrl, "publicUrl");
        requireNonBlank(canonicalUrl, "canonicalUrl");
        requireNonBlank(title, "title");
        requireNonBlank(summary, "summary");
        Objects.requireNonNull(publishedAt, "publishedAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
