package dev.persefonia.webpublic.projects;

import dev.persefonia.profileportfolio.application.query.PublicProjectCardView;
import java.util.List;
import java.util.Objects;

public record PublicProjectListingPage(
        String title,
        String htmlLanguage,
        String publicUrl,
        String canonicalUrl,
        boolean noindex,
        PublicProjectPageCopy copy,
        List<String> stylesheetPaths,
        List<PublicProjectCardView> projects) {
    public PublicProjectListingPage {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(htmlLanguage, "htmlLanguage");
        Objects.requireNonNull(publicUrl, "publicUrl");
        Objects.requireNonNull(canonicalUrl, "canonicalUrl");
        Objects.requireNonNull(copy, "copy");
        stylesheetPaths = List.copyOf(stylesheetPaths);
        projects = List.copyOf(projects);
    }
}
