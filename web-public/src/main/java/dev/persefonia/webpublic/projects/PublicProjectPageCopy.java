package dev.persefonia.webpublic.projects;

import java.util.Locale;
import java.util.Objects;

public record PublicProjectPageCopy(
        String projectsTitle,
        String projectsEmptyMessage,
        String technologiesLabel,
        String tagsLabel,
        String linksLabel,
        String caseStudyLabel,
        String viewProjectLabel,
        String featuredProjectsTitle) {
    public PublicProjectPageCopy {
        Objects.requireNonNull(projectsTitle, "projectsTitle");
        Objects.requireNonNull(projectsEmptyMessage, "projectsEmptyMessage");
        Objects.requireNonNull(technologiesLabel, "technologiesLabel");
        Objects.requireNonNull(tagsLabel, "tagsLabel");
        Objects.requireNonNull(linksLabel, "linksLabel");
        Objects.requireNonNull(caseStudyLabel, "caseStudyLabel");
        Objects.requireNonNull(viewProjectLabel, "viewProjectLabel");
        Objects.requireNonNull(featuredProjectsTitle, "featuredProjectsTitle");
    }

    public static PublicProjectPageCopy forLanguage(String language) {
        return switch (Objects.requireNonNull(language, "language").toUpperCase(Locale.ROOT)) {
            case "TR" -> new PublicProjectPageCopy(
                    "Projeler",
                    "Henüz herkese açık proje bulunmuyor.",
                    "Teknolojiler",
                    "Etiketler",
                    "Bağlantılar",
                    "Vaka çalışması",
                    "Projeyi görüntüle",
                    "Öne çıkan projeler");
            case "EN" -> new PublicProjectPageCopy(
                    "Projects",
                    "No public projects are currently available.",
                    "Technologies",
                    "Tags",
                    "Links",
                    "Case study",
                    "View project",
                    "Featured projects");
            default -> throw new IllegalArgumentException("Unsupported public project language: " + language);
        };
    }
}
