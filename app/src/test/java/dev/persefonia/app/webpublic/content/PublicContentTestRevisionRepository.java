package dev.persefonia.app.webpublic.content;

import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.revision.ContentRevision;
import dev.persefonia.contentpublishing.domain.revision.ContentRevisionId;
import dev.persefonia.contentpublishing.domain.revision.RevisionNumber;
import dev.persefonia.contentpublishing.domain.revision.port.ContentRevisionRepository;
import java.util.List;
import java.util.Optional;

final class PublicContentTestRevisionRepository implements ContentRevisionRepository {
    @Override
    public ContentRevision save(ContentRevision revision) {
        return revision;
    }

    @Override
    public Optional<ContentRevision> findById(ContentRevisionId id) {
        return Optional.empty();
    }

    @Override
    public List<ContentRevision> findByContentId(ContentId contentId) {
        return List.of();
    }

    @Override
    public Optional<RevisionNumber> findLatestRevisionNumber(ContentId contentId) {
        return Optional.empty();
    }
}
