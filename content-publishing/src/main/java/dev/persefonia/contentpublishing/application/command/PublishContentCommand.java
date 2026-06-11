package dev.persefonia.contentpublishing.application.command;

import dev.persefonia.contentpublishing.application.authorization.ContentCommandActor;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.revision.ChangeNote;
import java.time.Instant;
import java.util.Objects;

public record PublishContentCommand(
        ContentCommandActor actor,
        ContentId contentId,
        Instant requestedAt,
        ChangeNote changeNote) {
    public PublishContentCommand {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(contentId, "contentId");
        Objects.requireNonNull(requestedAt, "requestedAt");
    }
}
