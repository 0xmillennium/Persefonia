package dev.persefonia.taxonomy.application.command;

import dev.persefonia.taxonomy.domain.model.TagId;
import dev.persefonia.taxonomy.domain.model.TagStatus;
import java.time.Instant;
import dev.persefonia.taxonomy.domain.model.TagSlug;
import java.util.Optional;

public record TagCommandResult(
        TagId tagId, TagStatus status, Instant updatedAt, boolean mutated,
        Optional<TagSlug> oldSlug, TagSlug currentSlug) {
    public TagCommandResult(TagId tagId, TagStatus status, Instant updatedAt) {
        this(tagId, status, updatedAt, true, Optional.empty(), null);
    }

    public TagCommandResult(TagId tagId, TagStatus status, Instant updatedAt, boolean mutated) {
        this(tagId, status, updatedAt, mutated, Optional.empty(), null);
    }
}
