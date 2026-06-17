package dev.persefonia.profileportfolio.application.port;

import dev.persefonia.profileportfolio.domain.common.TagId;
import java.util.List;
import java.util.Set;

public interface ProjectTagVocabularyPort {
    List<ProjectTagOption> findAssignableTags();

    List<ProjectTagDetails> findByIds(Set<TagId> ids);

    ProjectTagAssignmentValidation validateAssignments(Set<TagId> currentlyAssigned, Set<TagId> requested);
}
