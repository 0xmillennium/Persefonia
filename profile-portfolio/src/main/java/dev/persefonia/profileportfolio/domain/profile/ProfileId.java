package dev.persefonia.profileportfolio.domain.profile;

import java.util.Objects;
import java.util.UUID;

public record ProfileId(UUID value) {
    public ProfileId {
        Objects.requireNonNull(value, "value");
    }

    public static ProfileId from(UUID value) {
        return new ProfileId(value);
    }

    public static ProfileId newId() {
        return new ProfileId(UUID.randomUUID());
    }
}
