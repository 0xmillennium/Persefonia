package dev.persefonia.identityaccess.domain.admin;

import java.util.Objects;
import java.util.UUID;

public record AdminAccountId(UUID value) {
    public AdminAccountId {
        Objects.requireNonNull(value, "value");
    }

    public static AdminAccountId newId() {
        return new AdminAccountId(UUID.randomUUID());
    }

    public static AdminAccountId of(UUID value) {
        return new AdminAccountId(value);
    }
}
