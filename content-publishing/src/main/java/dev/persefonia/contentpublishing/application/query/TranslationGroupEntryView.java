package dev.persefonia.contentpublishing.application.query;

import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.translation.TranslationGroupEntryId;
import java.util.Objects;
import java.util.Optional;

public record TranslationGroupEntryView(
        TranslationGroupEntryId entryId,
        ContentId contentItemId,
        ContentLanguage language,
        Optional<String> title) {
    public TranslationGroupEntryView {
        Objects.requireNonNull(entryId, "entryId");
        Objects.requireNonNull(contentItemId, "contentItemId");
        Objects.requireNonNull(language, "language");
        title = Optional.ofNullable(title).flatMap(value -> value);
    }
}
