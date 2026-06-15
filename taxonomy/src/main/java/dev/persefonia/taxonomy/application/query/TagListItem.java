package dev.persefonia.taxonomy.application.query;

import dev.persefonia.taxonomy.domain.model.TagId;
import dev.persefonia.taxonomy.domain.model.TagStatus;
import java.time.Instant;

public record TagListItem(
        TagId tagId, String name, String slug, TagStatus status, Instant createdAt, Instant updatedAt) {
}
