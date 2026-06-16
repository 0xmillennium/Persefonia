package dev.persefonia.profileportfolio.domain.profile;

import java.util.Objects;
import java.util.UUID;

public record ExternalProfileLinkId(UUID value) {
    public ExternalProfileLinkId {
        Objects.requireNonNull(value, "value");
    }

    public static ExternalProfileLinkId from(UUID value) {
        return new ExternalProfileLinkId(value);
    }

    public static ExternalProfileLinkId newId() {
        return new ExternalProfileLinkId(UUID.randomUUID());
    }
}
