package dev.persefonia.discovery.application.index;

import dev.persefonia.discovery.application.contract.PublicUrl;
import java.util.Objects;

public record PublicHomepageContentEntry(String title, String summary, String publicUrl, java.util.Optional<java.util.UUID> sourceId) {
    public PublicHomepageContentEntry(String title, String summary, String publicUrl) {
        this(title, summary, publicUrl, java.util.Optional.empty());
    }
    public PublicHomepageContentEntry {
        sourceId = Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(publicUrl, "publicUrl");
        if (title.isBlank() || summary.isBlank()) {
            throw new IllegalArgumentException("Homepage content requires a title, summary, and public path");
        }
        new PublicUrl(publicUrl);
    }
}
