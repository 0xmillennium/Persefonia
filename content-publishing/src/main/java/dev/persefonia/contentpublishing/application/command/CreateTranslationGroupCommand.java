package dev.persefonia.contentpublishing.application.command;

import dev.persefonia.contentpublishing.application.authorization.ContentCommandActor;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import java.time.Instant;
import java.util.Objects;

public record CreateTranslationGroupCommand(
        ContentCommandActor actor,
        ContentId initialContentItemId,
        Instant createdAt) {
    public CreateTranslationGroupCommand {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(initialContentItemId, "initialContentItemId");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
