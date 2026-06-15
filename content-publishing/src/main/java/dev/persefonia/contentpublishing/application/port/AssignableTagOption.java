package dev.persefonia.contentpublishing.application.port;

import dev.persefonia.contentpublishing.domain.content.ReferencedTagId;
import java.util.Objects;

public record AssignableTagOption(ReferencedTagId id, String name, String slug) {
    public AssignableTagOption {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(slug, "slug");
    }
}
