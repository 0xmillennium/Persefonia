package dev.persefonia.taxonomy.application.command;

import dev.persefonia.taxonomy.application.authorization.TaxonomyCommandActor;
import dev.persefonia.taxonomy.domain.model.TagId;
import java.time.Instant;
import java.util.Objects;

public record UpdateTagCommand(
        TaxonomyCommandActor actor,
        TagId tagId,
        String name,
        String slug,
        String description,
        Instant requestedAt) {
    public UpdateTagCommand {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(tagId, "tagId");
        Objects.requireNonNull(requestedAt, "requestedAt");
    }
}
