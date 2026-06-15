package dev.persefonia.taxonomy.application.command;

import dev.persefonia.taxonomy.application.authorization.TaxonomyCommandActor;
import dev.persefonia.taxonomy.domain.model.TagId;
import java.time.Instant;
import java.util.Objects;

public record ArchiveTagCommand(TaxonomyCommandActor actor, TagId tagId, Instant requestedAt) {
    public ArchiveTagCommand {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(tagId, "tagId");
        Objects.requireNonNull(requestedAt, "requestedAt");
    }
}
