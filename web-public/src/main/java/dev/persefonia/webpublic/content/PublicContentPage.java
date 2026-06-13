package dev.persefonia.webpublic.content;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record PublicContentPage(
        String title,
        String displayTitle,
        String summary,
        String typeLabel,
        String htmlLanguage,
        String canonicalPath,
        String seoTitle,
        String seoDescription,
        String canonicalUrl,
        String openGraphTitle,
        String openGraphDescription,
        String openGraphUrl,
        String openGraphType,
        String publishedAtIso,
        String updatedAtIso,
        String publishedAtDisplay,
        String readingTimeLabel,
        String renderedHtml,
        boolean containsMermaid,
        boolean noindex,
        List<String> stylesheetPaths,
        Optional<String> mermaidScriptPath,
        List<PublicContentHeadingView> headings) {
    public PublicContentPage {
        title = Objects.requireNonNull(title, "title");
        displayTitle = Objects.requireNonNull(displayTitle, "displayTitle");
        summary = Objects.requireNonNull(summary, "summary");
        typeLabel = Objects.requireNonNull(typeLabel, "typeLabel");
        htmlLanguage = Objects.requireNonNull(htmlLanguage, "htmlLanguage");
        canonicalPath = Objects.requireNonNull(canonicalPath, "canonicalPath");
        seoTitle = Objects.requireNonNull(seoTitle, "seoTitle");
        seoDescription = Objects.requireNonNull(seoDescription, "seoDescription");
        canonicalUrl = Objects.requireNonNull(canonicalUrl, "canonicalUrl");
        openGraphTitle = Objects.requireNonNull(openGraphTitle, "openGraphTitle");
        openGraphDescription = Objects.requireNonNull(openGraphDescription, "openGraphDescription");
        openGraphUrl = Objects.requireNonNull(openGraphUrl, "openGraphUrl");
        openGraphType = Objects.requireNonNull(openGraphType, "openGraphType");
        publishedAtIso = Objects.requireNonNull(publishedAtIso, "publishedAtIso");
        updatedAtIso = Objects.requireNonNull(updatedAtIso, "updatedAtIso");
        publishedAtDisplay = Objects.requireNonNull(publishedAtDisplay, "publishedAtDisplay");
        readingTimeLabel = Objects.requireNonNull(readingTimeLabel, "readingTimeLabel");
        renderedHtml = Objects.requireNonNull(renderedHtml, "renderedHtml");
        stylesheetPaths = List.copyOf(Objects.requireNonNull(stylesheetPaths, "stylesheetPaths"));
        mermaidScriptPath = Objects.requireNonNull(mermaidScriptPath, "mermaidScriptPath");
        headings = List.copyOf(Objects.requireNonNull(headings, "headings"));
    }
}
