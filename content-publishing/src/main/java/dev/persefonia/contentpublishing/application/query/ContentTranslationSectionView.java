package dev.persefonia.contentpublishing.application.query;

import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ContentTranslationSectionView(
        ContentId contentItemId,
        ContentType contentType,
        Optional<TranslationGroupDetailsView> group,
        List<TranslationCandidateItem> candidates) {
    public ContentTranslationSectionView {
        Objects.requireNonNull(contentItemId, "contentItemId");
        Objects.requireNonNull(contentType, "contentType");
        group = Optional.ofNullable(group).flatMap(value -> value);
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
    }

    public boolean hasGroup() {
        return group.isPresent();
    }
}
