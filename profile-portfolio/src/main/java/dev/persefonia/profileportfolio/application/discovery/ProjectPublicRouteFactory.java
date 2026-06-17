package dev.persefonia.profileportfolio.application.discovery;

import dev.persefonia.discovery.application.contract.PublicUrl;
import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.project.ProjectSlug;
import java.util.Objects;

public final class ProjectPublicRouteFactory {
    public PublicUrl publicUrl(ContentLanguage language, ProjectSlug slug) {
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(slug, "slug");
        return new PublicUrl("/" + languageSegment(language) + "/projects/" + slug.value());
    }

    private static String languageSegment(ContentLanguage language) {
        return switch (language) {
            case TR -> "tr";
            case EN -> "en";
        };
    }
}
