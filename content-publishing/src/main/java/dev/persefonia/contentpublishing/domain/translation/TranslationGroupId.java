package dev.persefonia.contentpublishing.domain.translation;

import java.util.Objects;
import java.util.UUID;

public record TranslationGroupId(UUID value) {
    public TranslationGroupId {
        Objects.requireNonNull(value, "value");
    }

    public static TranslationGroupId newId() {
        return new TranslationGroupId(UUID.randomUUID());
    }

    public static TranslationGroupId from(UUID value) {
        return new TranslationGroupId(value);
    }
}
