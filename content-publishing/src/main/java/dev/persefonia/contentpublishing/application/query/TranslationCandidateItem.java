package dev.persefonia.contentpublishing.application.query;

import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import java.util.Objects;
import java.util.Optional;

public record TranslationCandidateItem(
        ContentId contentItemId,
        ContentLanguage language,
        Optional<String> title) {
    public TranslationCandidateItem {
        Objects.requireNonNull(contentItemId, "contentItemId");
        Objects.requireNonNull(language, "language");
        title = Optional.ofNullable(title).flatMap(value -> value);
    }
}
