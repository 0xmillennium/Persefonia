package dev.persefonia.contentpublishing.application.command;

import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentRenderSnapshot;
import dev.persefonia.contentpublishing.domain.content.ContentStatus;
import dev.persefonia.contentpublishing.domain.revision.RevisionNumber;
import java.time.Instant;

public record ContentPublishResult(
        ContentId contentId,
        ContentStatus status,
        ContentRenderSnapshot snapshot,
        RevisionNumber revisionNumber,
        Instant publishedAt) {
}
