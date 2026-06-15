package dev.persefonia.taxonomy.application.query;

import java.util.Objects;
import java.util.UUID;

public record PublicTagBySourceQuery(UUID tagId, String expectedSlug) {
    public PublicTagBySourceQuery {
        Objects.requireNonNull(tagId, "tagId");
        Objects.requireNonNull(expectedSlug, "expectedSlug");
        if (!expectedSlug.matches("^[a-z0-9]+(?:-[a-z0-9]+)*$")) {
            throw new IllegalArgumentException("expectedSlug must be a lowercase URL-safe slug");
        }
    }
}
