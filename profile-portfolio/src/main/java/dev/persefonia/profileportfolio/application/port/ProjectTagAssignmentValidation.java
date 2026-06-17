package dev.persefonia.profileportfolio.application.port;

import dev.persefonia.profileportfolio.domain.common.TagId;
import java.util.Set;

public record ProjectTagAssignmentValidation(Set<TagId> missingTagIds, Set<TagId> newlyArchivedTagIds) {
    public ProjectTagAssignmentValidation {
        missingTagIds = Set.copyOf(missingTagIds);
        newlyArchivedTagIds = Set.copyOf(newlyArchivedTagIds);
    }
}
