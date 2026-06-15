package dev.persefonia.contentpublishing.application.port;

import dev.persefonia.contentpublishing.domain.content.ReferencedTagId;
import java.util.Objects;

public record ReferencedTagDetails(ReferencedTagId id, String name, String slug, boolean archived) {
    public ReferencedTagDetails {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(slug, "slug");
    }
}
