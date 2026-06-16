package dev.persefonia.profileportfolio.application.command;

import java.util.List;
import java.util.Objects;

public record ProfileLocalizationInput(
        String language,
        String shortBio,
        String longBio,
        String locationText,
        List<TechnicalFocusAreaInput> technicalFocusAreas,
        List<EducationSummaryInput> educationSummaries,
        List<CurrentFocusItemInput> currentFocusItems) {
    public ProfileLocalizationInput {
        technicalFocusAreas = List.copyOf(Objects.requireNonNull(technicalFocusAreas, "technicalFocusAreas"));
        educationSummaries = List.copyOf(Objects.requireNonNull(educationSummaries, "educationSummaries"));
        currentFocusItems = List.copyOf(Objects.requireNonNull(currentFocusItems, "currentFocusItems"));
    }
}
