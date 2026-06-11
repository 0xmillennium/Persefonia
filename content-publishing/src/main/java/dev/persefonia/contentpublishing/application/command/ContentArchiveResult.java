package dev.persefonia.contentpublishing.application.command;

import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentStatus;
import java.time.Instant;

public record ContentArchiveResult(ContentId contentId, ContentStatus status, Instant archivedAt) {
}
