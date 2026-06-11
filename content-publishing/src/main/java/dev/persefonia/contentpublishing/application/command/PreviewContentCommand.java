package dev.persefonia.contentpublishing.application.command;

import dev.persefonia.contentpublishing.application.authorization.ContentCommandActor;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import java.time.Instant;
import java.util.Objects;

public record PreviewContentCommand(ContentCommandActor actor, ContentId contentId, Instant requestedAt) {
    public PreviewContentCommand {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(contentId, "contentId");
        Objects.requireNonNull(requestedAt, "requestedAt");
    }
}
