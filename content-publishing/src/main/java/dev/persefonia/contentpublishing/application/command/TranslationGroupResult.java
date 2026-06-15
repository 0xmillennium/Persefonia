package dev.persefonia.contentpublishing.application.command;

import dev.persefonia.contentpublishing.domain.translation.TranslationGroupId;
import java.util.Objects;

public record TranslationGroupResult(TranslationGroupId translationGroupId) {
    public TranslationGroupResult {
        Objects.requireNonNull(translationGroupId, "translationGroupId");
    }
}
