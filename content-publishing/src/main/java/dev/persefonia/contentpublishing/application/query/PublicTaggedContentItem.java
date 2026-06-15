package dev.persefonia.contentpublishing.application.query;

import java.time.Instant;
import java.util.Objects;

public record PublicTaggedContentItem(
        String title,
        String summary,
        String publicUrl,
        String canonicalUrl,
        String contentType,
        Instant publishedAt,
        String language) {
    public PublicTaggedContentItem {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(publicUrl, "publicUrl");
        Objects.requireNonNull(canonicalUrl, "canonicalUrl");
        Objects.requireNonNull(contentType, "contentType");
        Objects.requireNonNull(publishedAt, "publishedAt");
        Objects.requireNonNull(language, "language");
    }
}
