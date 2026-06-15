package dev.persefonia.contentpublishing.application.command;

import dev.persefonia.contentpublishing.application.authorization.ContentCommandActor;
import dev.persefonia.contentpublishing.domain.translation.TranslationGroupEntryId;
import dev.persefonia.contentpublishing.domain.translation.TranslationGroupId;
import java.time.Instant;
import java.util.Objects;

public record RemoveTranslationEntryCommand(
        ContentCommandActor actor,
        TranslationGroupId translationGroupId,
        TranslationGroupEntryId entryId,
        Instant removedAt) {
    public RemoveTranslationEntryCommand {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(translationGroupId, "translationGroupId");
        Objects.requireNonNull(entryId, "entryId");
        Objects.requireNonNull(removedAt, "removedAt");
    }
}
