package dev.persefonia.webpublic.series;

import java.util.List;
import java.util.Objects;

public record PublicSeriesPage(
        String title,
        String slug,
        String description,
        String status,
        String htmlLanguage,
        String publicUrl,
        String canonicalUrl,
        boolean noindex,
        List<String> stylesheetPaths,
        List<PublicSeriesEntryView> entries) {
    public PublicSeriesPage {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(htmlLanguage, "htmlLanguage");
        Objects.requireNonNull(publicUrl, "publicUrl");
        Objects.requireNonNull(canonicalUrl, "canonicalUrl");
        stylesheetPaths = List.copyOf(Objects.requireNonNull(stylesheetPaths, "stylesheetPaths"));
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
    }
}
