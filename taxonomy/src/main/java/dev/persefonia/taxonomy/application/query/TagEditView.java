package dev.persefonia.taxonomy.application.query;

import dev.persefonia.taxonomy.domain.model.TagId;
import dev.persefonia.taxonomy.domain.model.TagStatus;
import java.time.Instant;

public record TagEditView(
        TagId tagId,
        String name,
        String slug,
        String description,
        TagStatus status,
        Instant updatedAt) {
}
