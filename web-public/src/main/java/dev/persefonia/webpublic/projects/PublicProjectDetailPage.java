package dev.persefonia.webpublic.projects;

import dev.persefonia.profileportfolio.application.query.PublicProjectDetailView;
import java.util.List;
import java.util.Objects;

public record PublicProjectDetailPage(
        String title,
        String htmlLanguage,
        String publicUrl,
        String canonicalUrl,
        boolean noindex,
        List<String> stylesheetPaths,
        PublicProjectDetailView project) {
    public PublicProjectDetailPage {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(htmlLanguage, "htmlLanguage");
        Objects.requireNonNull(publicUrl, "publicUrl");
        Objects.requireNonNull(canonicalUrl, "canonicalUrl");
        stylesheetPaths = List.copyOf(stylesheetPaths);
        Objects.requireNonNull(project, "project");
    }
}
