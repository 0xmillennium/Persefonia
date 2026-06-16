package dev.persefonia.webpublic.series;

import java.util.Objects;

public record PublicSeriesEntryView(
        int position,
        String title,
        String summary,
        String publicUrl,
        String canonicalUrl,
        String contentType,
        String publishedAt,
        String language) {
    public PublicSeriesEntryView {
        if (position <= 0) {
            throw new IllegalArgumentException("position must be positive");
        }
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(publicUrl, "publicUrl");
        Objects.requireNonNull(canonicalUrl, "canonicalUrl");
        Objects.requireNonNull(contentType, "contentType");
        Objects.requireNonNull(publishedAt, "publishedAt");
        Objects.requireNonNull(language, "language");
    }
}
