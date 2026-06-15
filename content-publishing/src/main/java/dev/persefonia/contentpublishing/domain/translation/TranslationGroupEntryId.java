package dev.persefonia.contentpublishing.domain.translation;

import java.util.Objects;
import java.util.UUID;

public record TranslationGroupEntryId(UUID value) {
    public TranslationGroupEntryId {
        Objects.requireNonNull(value, "value");
    }

    public static TranslationGroupEntryId newId() {
        return new TranslationGroupEntryId(UUID.randomUUID());
    }

    public static TranslationGroupEntryId from(UUID value) {
        return new TranslationGroupEntryId(value);
    }
}
