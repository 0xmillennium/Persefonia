package dev.persefonia.taxonomy.domain.model;

import java.util.Objects;
import java.util.UUID;

public record TagId(UUID value) {
    public TagId {
        Objects.requireNonNull(value, "value");
    }

    public static TagId newId() {
        return new TagId(UUID.randomUUID());
    }

    public static TagId from(UUID value) {
        return new TagId(value);
    }
}
