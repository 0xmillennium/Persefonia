package dev.persefonia.contentpublishing.application.service;

import dev.persefonia.contentpublishing.application.authorization.ContentCommandAuthorizationPolicy;
import dev.persefonia.contentpublishing.application.query.ContentRevisionResult;
import dev.persefonia.contentpublishing.application.query.ListContentRevisionsQuery;
import dev.persefonia.contentpublishing.domain.revision.port.ContentRevisionRepository;
import java.util.List;
import java.util.Objects;

public final class ContentRevisionQueryHandler {
    private final ContentRevisionRepository revisions;
    private final ContentCommandAuthorizationPolicy authorization;

    public ContentRevisionQueryHandler(
            ContentRevisionRepository revisions,
            ContentCommandAuthorizationPolicy authorization) {
        this.revisions = Objects.requireNonNull(revisions, "revisions");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
    }

    public List<ContentRevisionResult> list(ListContentRevisionsQuery query) {
        authorization.requireOwner(query.actor(), "content.list-revisions");
        return revisions.findByContentId(query.contentId()).stream()
                .map(revision -> new ContentRevisionResult(
                        revision.id(),
                        revision.contentId(),
                        revision.revisionNumber(),
                        revision.revisionType(),
                        revision.renderedHtml(),
                        revision.createdBy(),
                        revision.createdAt(),
                        revision.changeNote()))
                .toList();
    }
}
