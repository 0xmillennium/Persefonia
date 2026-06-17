package dev.persefonia.profileportfolio.application.query;

import java.util.List;

public record PublicFeaturedProjectView(
        String title,
        String summary,
        String slug,
        String publicUrl,
        List<PublicProjectTechnologyView> technologies,
        List<PublicProjectTagView> tags) {
    public PublicFeaturedProjectView {
        technologies = List.copyOf(technologies);
        tags = List.copyOf(tags);
    }
}
