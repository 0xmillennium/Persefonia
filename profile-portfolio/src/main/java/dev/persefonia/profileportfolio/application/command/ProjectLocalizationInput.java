package dev.persefonia.profileportfolio.application.command;

import java.util.List;

public record ProjectLocalizationInput(
        String language,
        String slug,
        String title,
        String summary,
        List<ProjectCaseStudySectionInput> sections) {
    public ProjectLocalizationInput {
        sections = List.copyOf(sections == null ? List.of() : sections);
    }
}
