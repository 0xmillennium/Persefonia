package dev.persefonia.profileportfolio.domain.settings;

import java.util.Objects;
import java.util.UUID;

public record SitePresentationSettingsId(UUID value) {
    public SitePresentationSettingsId {
        Objects.requireNonNull(value, "value");
    }

    public static SitePresentationSettingsId from(UUID value) {
        return new SitePresentationSettingsId(value);
    }

    public static SitePresentationSettingsId newId() {
        return new SitePresentationSettingsId(UUID.randomUUID());
    }
}
