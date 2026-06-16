package dev.persefonia.contentpublishing.application.query;

import java.util.Objects;

public record PublicTranslationLink(
        String language,
        String label,
        String title,
        String publicUrl,
        String canonicalUrl) {
    public PublicTranslationLink {
        language = Objects.requireNonNull(language, "language");
        label = Objects.requireNonNull(label, "label");
        title = Objects.requireNonNull(title, "title");
        publicUrl = Objects.requireNonNull(publicUrl, "publicUrl");
        canonicalUrl = Objects.requireNonNull(canonicalUrl, "canonicalUrl");
    }
}
