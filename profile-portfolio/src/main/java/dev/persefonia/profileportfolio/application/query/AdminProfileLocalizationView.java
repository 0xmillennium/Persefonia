package dev.persefonia.profileportfolio.application.query;

import java.util.List;
import java.util.Objects;

public record AdminProfileLocalizationView(
        String language,
        String shortBio,
        String longBio,
        String locationText,
        List<AdminTechnicalFocusAreaView> technicalFocusAreas,
        List<AdminEducationSummaryView> educationSummaries,
        List<AdminCurrentFocusItemView> currentFocusItems) {
    public AdminProfileLocalizationView {
        technicalFocusAreas = List.copyOf(Objects.requireNonNull(technicalFocusAreas, "technicalFocusAreas"));
        educationSummaries = List.copyOf(Objects.requireNonNull(educationSummaries, "educationSummaries"));
        currentFocusItems = List.copyOf(Objects.requireNonNull(currentFocusItems, "currentFocusItems"));
    }
}
