package dev.persefonia.contentpublishing.application.port;

import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ReferencedTagId;
import java.time.Instant;
import java.util.Set;

public interface ContentTagAssignmentStore {
    Set<ReferencedTagId> findAssignedTagIds(ContentId contentId);

    void replaceAssignedTagIds(ContentId contentId, Set<ReferencedTagId> tagIds, Instant assignedAt);
}
