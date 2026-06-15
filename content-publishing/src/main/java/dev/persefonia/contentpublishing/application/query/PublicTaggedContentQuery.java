package dev.persefonia.contentpublishing.application.query;

import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.TagId;
import java.util.Objects;

public record PublicTaggedContentQuery(TagId tagId, ContentLanguage language, int limit) {
    public PublicTaggedContentQuery {
        Objects.requireNonNull(tagId, "tagId");
        Objects.requireNonNull(language, "language");
        if (limit < 1 || limit > 50) {
            throw new IllegalArgumentException("limit must be between 1 and 50");
        }
    }
}
