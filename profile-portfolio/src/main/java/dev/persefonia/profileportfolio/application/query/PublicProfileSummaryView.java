package dev.persefonia.profileportfolio.application.query;

import java.util.List;
import java.util.Objects;

public record PublicProfileSummaryView(
        String displayName,
        String shortBio,
        String locationText,
        List<PublicProfileExternalLinkView> externalLinks,
        List<PublicTechnicalFocusAreaView> technicalFocusAreas,
        List<PublicEducationSummaryView> educationSummaries,
        List<PublicCurrentFocusItemView> currentFocusItems) {
    public PublicProfileSummaryView {
        externalLinks = List.copyOf(Objects.requireNonNull(externalLinks, "externalLinks"));
        technicalFocusAreas = List.copyOf(Objects.requireNonNull(technicalFocusAreas, "technicalFocusAreas"));
        educationSummaries = List.copyOf(Objects.requireNonNull(educationSummaries, "educationSummaries"));
        currentFocusItems = List.copyOf(Objects.requireNonNull(currentFocusItems, "currentFocusItems"));
    }
}
