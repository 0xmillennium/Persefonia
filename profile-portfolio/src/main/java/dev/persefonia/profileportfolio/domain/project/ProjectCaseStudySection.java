package dev.persefonia.profileportfolio.domain.project;

import dev.persefonia.profileportfolio.domain.common.SortOrder;
import java.util.Objects;

public record ProjectCaseStudySection(
        ProjectCaseStudySectionId id,
        CaseStudySectionType type,
        CaseStudyText body,
        SortOrder sortOrder) {
    public ProjectCaseStudySection {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(sortOrder, "sortOrder");
    }
}
