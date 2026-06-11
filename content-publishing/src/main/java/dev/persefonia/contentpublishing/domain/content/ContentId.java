package dev.persefonia.contentpublishing.domain.content;

import java.util.Objects;
import java.util.UUID;

public record ContentId(UUID value) {
    public ContentId {
        Objects.requireNonNull(value, "value");
    }

    public static ContentId newId() {
        return new ContentId(UUID.randomUUID());
    }

    public static ContentId from(UUID value) {
        return new ContentId(value);
    }
}
