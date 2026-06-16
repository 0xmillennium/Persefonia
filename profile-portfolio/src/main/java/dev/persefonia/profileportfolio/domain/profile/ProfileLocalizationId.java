package dev.persefonia.profileportfolio.domain.profile;

import java.util.Objects;
import java.util.UUID;

public record ProfileLocalizationId(UUID value) {
    public ProfileLocalizationId {
        Objects.requireNonNull(value, "value");
    }

    public static ProfileLocalizationId from(UUID value) {
        return new ProfileLocalizationId(value);
    }

    public static ProfileLocalizationId newId() {
        return new ProfileLocalizationId(UUID.randomUUID());
    }
}
