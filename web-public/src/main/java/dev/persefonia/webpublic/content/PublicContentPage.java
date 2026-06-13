package dev.persefonia.webpublic.content;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record PublicContentPage(
        String title,
        String summary,
        Instant publishedAt,
        int readingTime,
        String typeLabel,
        String language,
        String canonicalPath,
        Optional<String> seoTitle,
        Optional<String> seoDescription,
        Optional<String> openGraphTitle,
        Optional<String> openGraphDescription,
        String renderedHtml,
        boolean containsMermaid,
        List<PublicContentHeadingView> headings) {
    public PublicContentPage {
        title = Objects.requireNonNull(title, "title");
        summary = Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(publishedAt, "publishedAt");
        if (readingTime < 1) {
            throw new IllegalArgumentException("readingTime must be positive");
        }
        typeLabel = Objects.requireNonNull(typeLabel, "typeLabel");
        language = Objects.requireNonNull(language, "language");
        canonicalPath = Objects.requireNonNull(canonicalPath, "canonicalPath");
        seoTitle = Objects.requireNonNull(seoTitle, "seoTitle");
        seoDescription = Objects.requireNonNull(seoDescription, "seoDescription");
        openGraphTitle = Objects.requireNonNull(openGraphTitle, "openGraphTitle");
        openGraphDescription = Objects.requireNonNull(openGraphDescription, "openGraphDescription");
        renderedHtml = Objects.requireNonNull(renderedHtml, "renderedHtml");
        headings = List.copyOf(Objects.requireNonNull(headings, "headings"));
    }
}
