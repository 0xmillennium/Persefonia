package dev.persefonia.taxonomy.application.command;

import dev.persefonia.taxonomy.domain.model.TagId;
import dev.persefonia.taxonomy.domain.model.TagStatus;
import java.time.Instant;

public record TagCommandResult(TagId tagId, TagStatus status, Instant updatedAt, boolean mutated) {
    public TagCommandResult(TagId tagId, TagStatus status, Instant updatedAt) {
        this(tagId, status, updatedAt, true);
    }
}
