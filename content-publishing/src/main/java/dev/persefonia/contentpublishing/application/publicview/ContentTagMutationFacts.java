package dev.persefonia.contentpublishing.application.publicview;

import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.TagId;
import java.util.Objects;
import java.util.Set;

public record ContentTagMutationFacts(
        ContentId contentId,
        ContentLanguage language,
        boolean publicListed,
        Set<TagId> oldTagIds,
        Set<TagId> newTagIds,
        boolean membershipChanged) {
    public ContentTagMutationFacts {
        Objects.requireNonNull(contentId, "contentId");
        Objects.requireNonNull(language, "language");
        oldTagIds = Set.copyOf(Objects.requireNonNull(oldTagIds, "oldTagIds"));
        newTagIds = Set.copyOf(Objects.requireNonNull(newTagIds, "newTagIds"));
    }
}
