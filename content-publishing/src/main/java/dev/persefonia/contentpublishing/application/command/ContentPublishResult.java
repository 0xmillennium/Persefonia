package dev.persefonia.contentpublishing.application.command;

import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentRenderSnapshot;
import dev.persefonia.contentpublishing.domain.content.ContentStatus;
import dev.persefonia.contentpublishing.domain.revision.RevisionNumber;
import java.time.Instant;
import dev.persefonia.contentpublishing.application.publicview.ContentPublicMutationFacts;
import dev.persefonia.contentpublishing.application.publicview.ContentPublicExposureSnapshot;
import java.util.Optional;

public record ContentPublishResult(
        ContentId contentId,
        ContentStatus status,
        ContentRenderSnapshot snapshot,
        RevisionNumber revisionNumber,
        Instant publishedAt,
        ContentPublicMutationFacts publicMutationFacts) {
    public ContentPublishResult(
            ContentId contentId, ContentStatus status, ContentRenderSnapshot snapshot,
            RevisionNumber revisionNumber, Instant publishedAt) {
        this(contentId, status, snapshot, revisionNumber, publishedAt,
                new ContentPublicMutationFacts(contentId, ContentPublicExposureSnapshot.none(),
                        ContentPublicExposureSnapshot.none(), Optional.empty(), Optional.empty()));
    }
}
