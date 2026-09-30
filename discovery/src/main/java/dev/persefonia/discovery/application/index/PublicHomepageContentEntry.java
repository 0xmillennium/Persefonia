package dev.persefonia.discovery.application.index;

import dev.persefonia.discovery.application.contract.PublicUrl;
import java.util.Objects;

public record PublicHomepageContentEntry(String title, String summary, String publicUrl) {
    public PublicHomepageContentEntry {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(publicUrl, "publicUrl");
        if (title.isBlank() || summary.isBlank()) {
            throw new IllegalArgumentException("Homepage content requires a title, summary, and public path");
        }
        new PublicUrl(publicUrl);
    }
}
