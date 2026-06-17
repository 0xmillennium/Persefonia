package dev.persefonia.profileportfolio.application.query;

import java.util.List;

public record AdminProjectLocalizationView(
        String language,
        String slug,
        String title,
        String summary,
        List<AdminProjectCaseStudySectionView> sections) {
    public AdminProjectLocalizationView {
        sections = List.copyOf(sections);
    }
}
