package dev.persefonia.contentpublishing.application.service;

import dev.persefonia.contentpublishing.application.authorization.ContentCommandAuthorizationPolicy;
import dev.persefonia.contentpublishing.application.query.ContentRevisionHistoryItem;
import dev.persefonia.contentpublishing.application.query.ContentRevisionHistoryResult;
import dev.persefonia.contentpublishing.application.query.ContentRevisionResult;
import dev.persefonia.contentpublishing.application.query.ListContentRevisionsQuery;
import dev.persefonia.contentpublishing.domain.content.port.ContentItemRepository;
import dev.persefonia.contentpublishing.domain.revision.ContentRevision;
import dev.persefonia.contentpublishing.domain.revision.port.ContentRevisionRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class ContentRevisionQueryHandler {
    private final ContentItemRepository contentItems;
    private final ContentRevisionRepository revisions;
    private final ContentCommandAuthorizationPolicy authorization;

    public ContentRevisionQueryHandler(
            ContentItemRepository contentItems,
            ContentRevisionRepository revisions,
            ContentCommandAuthorizationPolicy authorization) {
        this.contentItems = Objects.requireNonNull(contentItems, "contentItems");
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

    public ContentRevisionHistoryResult history(ListContentRevisionsQuery query) {
        authorization.requireOwner(query.actor(), "content.list-revisions");
        var content = ContentApplicationSupport.requiredContent(contentItems, query.contentId());
        var history = revisions.findByContentId(query.contentId()).stream()
                .sorted(Comparator.comparingInt(
                        (ContentRevision revision) -> revision.revisionNumber().value()).reversed())
                .map(revision -> new ContentRevisionHistoryItem(
                        revision.revisionNumber().value(),
                        revision.revisionType().name(),
                        revision.title().value(),
                        revision.slug().value(),
                        revision.createdBy().value().toString(),
                        revision.createdAt(),
                        revision.changeNote().map(note -> note.value()),
                        revision.renderedHtml().isPresent()))
                .toList();
        return new ContentRevisionHistoryResult(
                content.id(),
                content.status().name(),
                content.title().map(title -> title.value()),
                history);
    }
}
