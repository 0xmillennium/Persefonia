package dev.persefonia.communication.domain.contact;

import java.util.Objects;
import java.util.UUID;

public record AdminAccountId(UUID value) {
    public AdminAccountId {
        Objects.requireNonNull(value, "value");
    }

    public static AdminAccountId from(UUID value) {
        return new AdminAccountId(value);
    }

    public static AdminAccountId newId() {
        return new AdminAccountId(UUID.randomUUID());
    }
}
