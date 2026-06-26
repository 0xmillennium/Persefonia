package dev.persefonia.audit.domain.record;

import java.util.Objects;
import java.util.UUID;

public record SourceEntityId(UUID value) {
    public SourceEntityId {
        Objects.requireNonNull(value, "value");
    }

    public static SourceEntityId from(UUID value) {
        return new SourceEntityId(value);
    }
}
