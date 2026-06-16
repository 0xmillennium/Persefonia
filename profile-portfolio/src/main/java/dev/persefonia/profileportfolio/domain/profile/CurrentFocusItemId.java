package dev.persefonia.profileportfolio.domain.profile;

import java.util.Objects;
import java.util.UUID;

public record CurrentFocusItemId(UUID value) {
    public CurrentFocusItemId {
        Objects.requireNonNull(value, "value");
    }

    public static CurrentFocusItemId from(UUID value) {
        return new CurrentFocusItemId(value);
    }

    public static CurrentFocusItemId newId() {
        return new CurrentFocusItemId(UUID.randomUUID());
    }
}
