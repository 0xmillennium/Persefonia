package dev.persefonia.contentpublishing.application.exception;

import dev.persefonia.contentpublishing.domain.translation.TranslationGroupId;

public final class TranslationGroupNotFoundException extends ContentApplicationException {
    public TranslationGroupNotFoundException(TranslationGroupId translationGroupId) {
        super("Translation group not found: " + translationGroupId.value());
    }
}
