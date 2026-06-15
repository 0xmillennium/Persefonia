package dev.persefonia.taxonomy.application.query;

import dev.persefonia.taxonomy.domain.model.TagId;
import dev.persefonia.taxonomy.domain.model.TagStatus;
import java.util.Objects;

public record TagVocabularyItem(TagId id, String name, String slug, TagStatus status) {
    public TagVocabularyItem {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(status, "status");
    }
}
