package dev.persefonia.webpublic.tags;

import java.util.Objects;

public record PublicTagContentItemView(
        String title,
        String summary,
        String publicUrl,
        String canonicalUrl,
        String contentType,
        String publishedAt) {
    public PublicTagContentItemView {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(publicUrl, "publicUrl");
        Objects.requireNonNull(canonicalUrl, "canonicalUrl");
        Objects.requireNonNull(contentType, "contentType");
        Objects.requireNonNull(publishedAt, "publishedAt");
    }
}
