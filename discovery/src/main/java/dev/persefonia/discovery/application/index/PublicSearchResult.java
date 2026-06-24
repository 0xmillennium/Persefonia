package dev.persefonia.discovery.application.index;

import dev.persefonia.discovery.application.contract.DiscoveryLanguage;
import java.time.Instant;
import java.util.Objects;

public record PublicSearchResult(
        String sourceType,
        String sourceId,
        DiscoveryLanguage language,
        String publicUrl,
        String canonicalUrl,
        String title,
        String summary,
        Instant publishedAt,
        Instant sourceUpdatedAt,
        double rank) {
    public PublicSearchResult {
        requireNonBlank(sourceType, "sourceType");
        requireNonBlank(sourceId, "sourceId");
        Objects.requireNonNull(language, "language");
        requireNonBlank(publicUrl, "publicUrl");
        requireNonBlank(canonicalUrl, "canonicalUrl");
        requireNonBlank(title, "title");
        requireNonBlank(summary, "summary");
        if (rank < 0.0d || Double.isNaN(rank) || Double.isInfinite(rank)) {
            throw new IllegalArgumentException("rank must be a finite non-negative value");
        }
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
