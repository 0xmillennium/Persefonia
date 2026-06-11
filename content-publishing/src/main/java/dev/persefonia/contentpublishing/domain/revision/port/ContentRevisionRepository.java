package dev.persefonia.contentpublishing.domain.revision.port;

import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.revision.ContentRevision;
import dev.persefonia.contentpublishing.domain.revision.ContentRevisionId;
import dev.persefonia.contentpublishing.domain.revision.RevisionNumber;
import java.util.List;
import java.util.Optional;

public interface ContentRevisionRepository {
    ContentRevision save(ContentRevision revision);

    Optional<ContentRevision> findById(ContentRevisionId id);

    List<ContentRevision> findByContentId(ContentId contentId);

    Optional<RevisionNumber> findLatestRevisionNumber(ContentId contentId);
}
