package dev.persefonia.contentpublishing.application.command;

import dev.persefonia.contentpublishing.application.authorization.ContentCommandActor;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.translation.TranslationGroupId;
import java.time.Instant;
import java.util.Objects;

public record AddTranslationEntryCommand(
        ContentCommandActor actor,
        TranslationGroupId translationGroupId,
        ContentId contentItemId,
        Instant addedAt) {
    public AddTranslationEntryCommand {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(translationGroupId, "translationGroupId");
        Objects.requireNonNull(contentItemId, "contentItemId");
        Objects.requireNonNull(addedAt, "addedAt");
    }
}
