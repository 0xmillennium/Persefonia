package dev.persefonia.contentpublishing.application.command;

import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentStatus;
import java.time.Instant;
import dev.persefonia.contentpublishing.application.publicview.ContentPublicMutationFacts;
import dev.persefonia.contentpublishing.application.publicview.ContentPublicExposureSnapshot;
import java.util.Optional;

public record ContentUnpublishResult(
        ContentId contentId, ContentStatus status, Instant unpublishedAt,
        ContentPublicMutationFacts publicMutationFacts) {
    public ContentUnpublishResult(ContentId contentId, ContentStatus status, Instant unpublishedAt) {
        this(contentId, status, unpublishedAt,
                new ContentPublicMutationFacts(contentId, ContentPublicExposureSnapshot.none(),
                        ContentPublicExposureSnapshot.none(), Optional.empty(), Optional.empty()));
    }
}
