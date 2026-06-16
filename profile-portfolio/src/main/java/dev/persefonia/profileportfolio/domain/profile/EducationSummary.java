package dev.persefonia.profileportfolio.domain.profile;

import dev.persefonia.profileportfolio.domain.common.SortOrder;
import java.util.Objects;

public record EducationSummary(
        EducationSummaryId id,
        InstitutionName institution,
        ProgramName program,
        EducationDescription description,
        SortOrder sortOrder) {
    public EducationSummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(institution, "institution");
        Objects.requireNonNull(program, "program");
        Objects.requireNonNull(sortOrder, "sortOrder");
    }
}
