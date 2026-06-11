package dev.persefonia.contentpublishing.domain.common;

import java.util.Objects;
import java.util.UUID;

public record AdminIdentityRef(UUID value) {
    public AdminIdentityRef {
        Objects.requireNonNull(value, "value");
    }

    public static AdminIdentityRef newId() {
        return new AdminIdentityRef(UUID.randomUUID());
    }

    public static AdminIdentityRef from(UUID value) {
        return new AdminIdentityRef(value);
    }
}
