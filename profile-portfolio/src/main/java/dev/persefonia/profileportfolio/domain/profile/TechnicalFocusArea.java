package dev.persefonia.profileportfolio.domain.profile;

import dev.persefonia.profileportfolio.domain.common.SortOrder;
import java.util.Objects;

public record TechnicalFocusArea(
        TechnicalFocusAreaId id,
        FocusAreaName name,
        FocusAreaDescription description,
        SortOrder sortOrder) {
    public TechnicalFocusArea {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(sortOrder, "sortOrder");
    }
}
