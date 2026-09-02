package dev.persefonia.contentpublishing.application.port;

import dev.persefonia.contentpublishing.domain.content.TagId;
import java.util.Objects;

public record ReferencedTagDetails(TagId id, String name, String slug, boolean archived) {
    public ReferencedTagDetails {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(slug, "slug");
    }
}
