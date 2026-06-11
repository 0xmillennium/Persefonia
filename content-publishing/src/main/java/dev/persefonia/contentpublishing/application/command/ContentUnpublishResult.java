package dev.persefonia.contentpublishing.application.command;

import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentStatus;
import java.time.Instant;

public record ContentUnpublishResult(ContentId contentId, ContentStatus status, Instant unpublishedAt) {
}
