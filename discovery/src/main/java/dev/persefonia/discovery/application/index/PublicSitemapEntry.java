package dev.persefonia.discovery.application.index;

import dev.persefonia.discovery.application.contract.DiscoveryLanguage;
import java.time.Instant;
import java.util.Objects;

public record PublicSitemapEntry(
        String publicUrl,
        String canonicalUrl,
        DiscoveryLanguage language,
        Instant lastModifiedAt) {
    public PublicSitemapEntry {
        requireNonBlank(publicUrl, "publicUrl");
        requireNonBlank(canonicalUrl, "canonicalUrl");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(lastModifiedAt, "lastModifiedAt");
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
