package dev.persefonia.discovery.application.contract;

import java.util.Objects;
import java.util.UUID;

public record SourceEntityId(UUID value) {
    public SourceEntityId {
        Objects.requireNonNull(value, "value");
    }
}
