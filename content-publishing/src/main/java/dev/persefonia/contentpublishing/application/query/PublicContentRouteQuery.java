package dev.persefonia.contentpublishing.application.query;

import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.content.Slug;
import java.util.Objects;

public record PublicContentRouteQuery(
        ContentType type,
        ContentLanguage language,
        Slug slug) {
    public PublicContentRouteQuery {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(slug, "slug");
    }
}
