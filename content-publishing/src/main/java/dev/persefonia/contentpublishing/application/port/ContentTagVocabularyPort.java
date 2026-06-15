package dev.persefonia.contentpublishing.application.port;

import dev.persefonia.contentpublishing.domain.content.ReferencedTagId;
import java.util.List;
import java.util.Set;

public interface ContentTagVocabularyPort {
    List<AssignableTagOption> findAssignableTags();

    List<ReferencedTagDetails> findByIds(Set<ReferencedTagId> ids);

    TagAssignmentValidation validateAssignments(
            Set<ReferencedTagId> currentlyAssigned,
            Set<ReferencedTagId> requested);
}
