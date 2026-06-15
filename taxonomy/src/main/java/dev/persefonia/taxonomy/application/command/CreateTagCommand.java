package dev.persefonia.taxonomy.application.command;

import dev.persefonia.taxonomy.application.authorization.TaxonomyCommandActor;
import java.time.Instant;
import java.util.Objects;

public record CreateTagCommand(
        TaxonomyCommandActor actor, String name, String slug, String description, Instant requestedAt) {
    public CreateTagCommand {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(requestedAt, "requestedAt");
    }
}
