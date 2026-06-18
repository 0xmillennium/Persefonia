package dev.persefonia.profileportfolio.domain.cv;

import java.util.Objects;
import java.util.UUID;

public record ActiveCvProfileId(UUID value) {
    public ActiveCvProfileId {
        Objects.requireNonNull(value, "value");
    }

    public static ActiveCvProfileId from(UUID value) {
        return new ActiveCvProfileId(value);
    }
}
