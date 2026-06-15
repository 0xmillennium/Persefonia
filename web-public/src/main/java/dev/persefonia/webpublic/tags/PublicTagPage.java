package dev.persefonia.webpublic.tags;

import java.util.List;
import java.util.Objects;

public record PublicTagPage(
        String name,
        String description,
        String status,
        String htmlLanguage,
        String publicUrl,
        String canonicalUrl,
        boolean noindex,
        List<String> stylesheetPaths,
        List<PublicTagContentItemView> items) {
    public PublicTagPage {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(htmlLanguage, "htmlLanguage");
        Objects.requireNonNull(publicUrl, "publicUrl");
        Objects.requireNonNull(canonicalUrl, "canonicalUrl");
        stylesheetPaths = List.copyOf(Objects.requireNonNull(stylesheetPaths, "stylesheetPaths"));
        items = List.copyOf(Objects.requireNonNull(items, "items"));
    }
}
