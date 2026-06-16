package dev.persefonia.profileportfolio.domain.profile;

import dev.persefonia.profileportfolio.domain.common.SortOrder;
import java.util.Objects;

public record CurrentFocusItem(
        CurrentFocusItemId id,
        FocusItemText text,
        SortOrder sortOrder) {
    public CurrentFocusItem {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(sortOrder, "sortOrder");
    }
}
