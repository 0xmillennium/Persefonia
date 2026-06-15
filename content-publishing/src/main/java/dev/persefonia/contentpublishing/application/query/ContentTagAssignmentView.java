package dev.persefonia.contentpublishing.application.query;

import dev.persefonia.contentpublishing.application.port.AssignableTagOption;
import dev.persefonia.contentpublishing.application.port.ReferencedTagDetails;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import java.util.List;
import java.util.Objects;

public record ContentTagAssignmentView(
        ContentId contentId,
        List<AssignableTagOption> assignableTags,
        List<ReferencedTagDetails> assignedTags) {
    public ContentTagAssignmentView {
        Objects.requireNonNull(contentId, "contentId");
        assignableTags = List.copyOf(Objects.requireNonNull(assignableTags, "assignableTags"));
        assignedTags = List.copyOf(Objects.requireNonNull(assignedTags, "assignedTags"));
    }
}
