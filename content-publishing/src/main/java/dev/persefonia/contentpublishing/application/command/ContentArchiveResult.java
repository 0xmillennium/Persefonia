package dev.persefonia.contentpublishing.application.command;

import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentStatus;
import java.time.Instant;
import dev.persefonia.contentpublishing.application.publicview.ContentPublicMutationFacts;
import dev.persefonia.contentpublishing.application.publicview.ContentPublicExposureSnapshot;
import java.util.Optional;

public record ContentArchiveResult(
        ContentId contentId, ContentStatus status, Instant archivedAt,
        ContentPublicMutationFacts publicMutationFacts) {
    public ContentArchiveResult(ContentId contentId, ContentStatus status, Instant archivedAt) {
        this(contentId, status, archivedAt,
                new ContentPublicMutationFacts(contentId, ContentPublicExposureSnapshot.none(),
                        ContentPublicExposureSnapshot.none(), Optional.empty(), Optional.empty()));
    }
}
