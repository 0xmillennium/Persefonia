package dev.persefonia.app.webadmin.content;

import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.revision.ContentRevision;
import dev.persefonia.contentpublishing.domain.revision.ContentRevisionId;
import dev.persefonia.contentpublishing.domain.revision.RevisionNumber;
import dev.persefonia.contentpublishing.domain.revision.port.ContentRevisionRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

final class AdminContentTestRevisionRepository implements ContentRevisionRepository {
    private final List<ContentRevision> revisions = new ArrayList<>();

    @Override
    public ContentRevision save(ContentRevision revision) {
        revisions.add(revision);
        return revision;
    }

    @Override
    public Optional<ContentRevision> findById(ContentRevisionId id) {
        return revisions.stream().filter(revision -> revision.id().equals(id)).findFirst();
    }

    @Override
    public List<ContentRevision> findByContentId(ContentId contentId) {
        return revisions.stream()
                .filter(revision -> revision.contentId().equals(contentId))
                .sorted(Comparator.comparingInt(revision -> revision.revisionNumber().value()))
                .toList();
    }

    @Override
    public Optional<RevisionNumber> findLatestRevisionNumber(ContentId contentId) {
        return findByContentId(contentId).stream()
                .map(revision -> revision.revisionNumber())
                .max(Comparator.comparingInt(revisionNumber -> revisionNumber.value()));
    }

    void reset() {
        revisions.clear();
    }
}
