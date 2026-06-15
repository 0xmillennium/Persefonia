package dev.persefonia.taxonomy.application.query;

import java.util.Objects;
import java.util.UUID;

public record PublicTagView(
        UUID tagId,
        String name,
        String slug,
        String description,
        String status) {
    public PublicTagView {
        Objects.requireNonNull(tagId, "tagId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(status, "status");
    }
}
