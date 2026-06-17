package dev.persefonia.profileportfolio.application.query;

import java.util.List;

public record PublicProjectDetailView(
        String title,
        String summary,
        String slug,
        String publicUrl,
        List<PublicProjectTechnologyView> technologies,
        List<PublicProjectLinkView> links,
        List<PublicProjectTagView> tags,
        List<PublicProjectCaseStudySectionView> sections) {
    public PublicProjectDetailView {
        technologies = List.copyOf(technologies);
        links = List.copyOf(links);
        tags = List.copyOf(tags);
        sections = List.copyOf(sections);
    }
}
