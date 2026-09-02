package dev.persefonia.contentpublishing.application.port;

import dev.persefonia.contentpublishing.domain.content.TagId;
import java.util.List;
import java.util.Set;

public interface ContentTagVocabularyPort {
    List<AssignableTagOption> findAssignableTags();

    List<ReferencedTagDetails> findByIds(Set<TagId> ids);

    TagAssignmentValidation validateAssignments(
            Set<TagId> currentlyAssigned,
            Set<TagId> requested);
}
