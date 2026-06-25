package dev.persefonia.communication.domain.contact;

import java.util.Objects;
import java.util.UUID;

public record ContactMessageId(UUID value) {
    public ContactMessageId {
        Objects.requireNonNull(value, "value");
    }

    public static ContactMessageId from(UUID value) {
        return new ContactMessageId(value);
    }

    public static ContactMessageId newId() {
        return new ContactMessageId(UUID.randomUUID());
    }
}
