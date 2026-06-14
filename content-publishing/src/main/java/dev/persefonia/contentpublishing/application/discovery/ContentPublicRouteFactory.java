package dev.persefonia.contentpublishing.application.discovery;

import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.content.Slug;
import dev.persefonia.discovery.application.contract.PublicUrl;
import java.util.Objects;

public final class ContentPublicRouteFactory {
    public PublicUrl publicUrl(ContentType type, ContentLanguage language, Slug slug) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(slug, "slug");
        return new PublicUrl("/" + languageSegment(language) + "/" + collectionSegment(type) + "/" + slug.value());
    }

    private static String languageSegment(ContentLanguage language) {
        return switch (language) {
            case TR -> "tr";
            case EN -> "en";
        };
    }

    private static String collectionSegment(ContentType type) {
        return switch (type) {
            case ARTICLE -> "articles";
            case NOTE -> "notes";
            case RESEARCH -> "research";
            case PAGE -> "pages";
        };
    }
}
