package dev.persefonia.contentpublishing.application.command;

import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.translation.TranslationGroupId;
import java.util.Objects;

public record TranslationGroupResult(TranslationGroupId translationGroupId, ContentId contentItemId) {
    public TranslationGroupResult(TranslationGroupId translationGroupId) {
        this(translationGroupId, null);
    }

    public TranslationGroupResult {
        Objects.requireNonNull(translationGroupId, "translationGroupId");
    }
}
