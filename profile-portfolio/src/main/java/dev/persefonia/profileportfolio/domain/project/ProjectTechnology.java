package dev.persefonia.profileportfolio.domain.project;

import dev.persefonia.profileportfolio.domain.common.SortOrder;
import java.util.Objects;

public record ProjectTechnology(
        ProjectTechnologyId id,
        TechnologyName name,
        NormalizedTechnologyName normalizedName,
        TechnologyCategory category,
        SortOrder sortOrder) {
    public ProjectTechnology {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(normalizedName, "normalizedName");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(sortOrder, "sortOrder");
    }
}
