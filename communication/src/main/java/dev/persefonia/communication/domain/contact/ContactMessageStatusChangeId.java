package dev.persefonia.communication.domain.contact;

import java.util.Objects;
import java.util.UUID;

public record ContactMessageStatusChangeId(UUID value) {
    public ContactMessageStatusChangeId {
        Objects.requireNonNull(value, "value");
    }

    public static ContactMessageStatusChangeId from(UUID value) {
        return new ContactMessageStatusChangeId(value);
    }

    public static ContactMessageStatusChangeId newId() {
        return new ContactMessageStatusChangeId(UUID.randomUUID());
    }
}
