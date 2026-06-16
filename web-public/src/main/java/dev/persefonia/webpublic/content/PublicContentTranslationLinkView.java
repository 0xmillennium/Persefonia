package dev.persefonia.webpublic.content;

import java.util.Objects;

public record PublicContentTranslationLinkView(
        String language,
        String label,
        String title,
        String publicUrl) {
    public PublicContentTranslationLinkView {
        language = Objects.requireNonNull(language, "language");
        label = Objects.requireNonNull(label, "label");
        title = Objects.requireNonNull(title, "title");
        publicUrl = Objects.requireNonNull(publicUrl, "publicUrl");
    }
}
