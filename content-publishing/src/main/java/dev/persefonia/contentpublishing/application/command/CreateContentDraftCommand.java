package dev.persefonia.contentpublishing.application.command;

import dev.persefonia.contentpublishing.application.authorization.ContentCommandActor;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import java.time.Instant;
import java.util.Objects;

public record CreateContentDraftCommand(
        ContentCommandActor actor,
        ContentType type,
        ContentVisibility visibility,
        ContentLanguage language,
        Instant requestedAt) {
    public CreateContentDraftCommand {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(requestedAt, "requestedAt");
    }
}
