package dev.persefonia.profileportfolio.application.query;

import java.util.List;

public record PublicProjectCardView(
        String title,
        String summary,
        String slug,
        String publicUrl,
        List<PublicProjectTechnologyView> technologies,
        List<PublicProjectTagView> tags) {
    public PublicProjectCardView {
        technologies = List.copyOf(technologies);
        tags = List.copyOf(tags);
    }
}
