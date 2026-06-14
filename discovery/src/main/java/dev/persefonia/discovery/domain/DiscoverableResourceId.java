package dev.persefonia.discovery.domain;

import java.util.Objects;
import java.util.UUID;

public record DiscoverableResourceId(UUID value) {
    public DiscoverableResourceId {
        Objects.requireNonNull(value, "value");
    }

    public static DiscoverableResourceId random() {
        return new DiscoverableResourceId(UUID.randomUUID());
    }
}
