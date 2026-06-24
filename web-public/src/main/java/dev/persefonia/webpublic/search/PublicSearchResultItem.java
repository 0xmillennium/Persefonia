package dev.persefonia.webpublic.search;

import java.time.Instant;
import java.util.Objects;

public record PublicSearchResultItem(
        String title,
        String summary,
        String publicUrl,
        String resourceType,
        String languageLabel,
        Instant publishedAt) {
    public PublicSearchResultItem {
        title = requireNonBlank(title, "title");
        summary = requireNonBlank(summary, "summary");
        publicUrl = requireSafePublicUrl(publicUrl);
        resourceType = requireNonBlank(resourceType, "resourceType");
        languageLabel = requireNonBlank(languageLabel, "languageLabel");
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static String requireSafePublicUrl(String value) {
        String publicUrl = requireNonBlank(value, "publicUrl");
        if (!publicUrl.startsWith("/") || publicUrl.startsWith("//")
                || publicUrl.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("publicUrl must be an application-relative path");
        }
        return publicUrl;
    }
}
