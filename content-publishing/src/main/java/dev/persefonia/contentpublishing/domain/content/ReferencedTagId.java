package dev.persefonia.contentpublishing.domain.content;

import java.util.Objects;
import java.util.UUID;

public record ReferencedTagId(UUID value) {
    public ReferencedTagId {
        Objects.requireNonNull(value, "value");
    }

    public static ReferencedTagId from(UUID value) {
        return new ReferencedTagId(value);
    }
}
