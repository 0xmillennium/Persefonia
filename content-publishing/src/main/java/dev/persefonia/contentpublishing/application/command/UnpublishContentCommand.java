package dev.persefonia.contentpublishing.application.command;

import dev.persefonia.contentpublishing.application.authorization.ContentCommandActor;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import java.time.Instant;
import java.util.Objects;

public record UnpublishContentCommand(ContentCommandActor actor, ContentId contentId, Instant requestedAt) {
    public UnpublishContentCommand {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(contentId, "contentId");
        Objects.requireNonNull(requestedAt, "requestedAt");
    }
}
