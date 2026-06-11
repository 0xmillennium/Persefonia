package dev.persefonia.contentpublishing.domain.revision;

import java.util.Objects;
import java.util.UUID;

public record ContentRevisionId(UUID value) {
    public ContentRevisionId {
        Objects.requireNonNull(value, "value");
    }

    public static ContentRevisionId newId() {
        return new ContentRevisionId(UUID.randomUUID());
    }

    public static ContentRevisionId from(UUID value) {
        return new ContentRevisionId(value);
    }
}
