package dev.persefonia.profileportfolio.application.query;

import java.util.List;

public record PublicFeaturedProjectView(
        String title,
        String summary,
        String slug,
        String publicUrl,
        List<PublicProjectTechnologyView> technologies,
        List<PublicProjectTagView> tags,
        java.util.Optional<String> coverAssetId) {
    public PublicFeaturedProjectView(String title, String summary, String slug, String publicUrl,
            List<PublicProjectTechnologyView> technologies, List<PublicProjectTagView> tags) {
        this(title, summary, slug, publicUrl, technologies, tags, java.util.Optional.empty());
    }
    public PublicFeaturedProjectView {
        java.util.Objects.requireNonNull(coverAssetId, "coverAssetId");
        technologies = List.copyOf(technologies);
        tags = List.copyOf(tags);
    }
}
