package dev.persefonia.contentpublishing.application.port;

import dev.persefonia.contentpublishing.domain.content.TagId;
import java.util.Objects;
import java.util.Set;

public record TagAssignmentValidation(
        Set<TagId> missingTagIds,
        Set<TagId> newlyArchivedTagIds) {
    public TagAssignmentValidation {
        missingTagIds = Set.copyOf(Objects.requireNonNull(missingTagIds, "missingTagIds"));
        newlyArchivedTagIds = Set.copyOf(Objects.requireNonNull(newlyArchivedTagIds, "newlyArchivedTagIds"));
    }

    public boolean valid() {
        return missingTagIds.isEmpty() && newlyArchivedTagIds.isEmpty();
    }
}
