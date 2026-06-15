package dev.persefonia.taxonomy.application.authorization;

import java.util.Objects;
import java.util.UUID;

public record TaxonomyCommandActor(UUID identityRef, boolean active, boolean owner) {
    public TaxonomyCommandActor {
        Objects.requireNonNull(identityRef, "identityRef");
    }
}
