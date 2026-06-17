package dev.persefonia.profileportfolio.application.service;

import dev.persefonia.profileportfolio.application.discovery.ProjectPublicRouteFactory;
import dev.persefonia.profileportfolio.application.port.ProjectPublicReadModel;
import dev.persefonia.profileportfolio.application.port.ProjectTagVocabularyPort;
import dev.persefonia.profileportfolio.application.query.PublicProjectCardView;
import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.project.ProjectSlug;
import java.util.List;
import java.util.Objects;

public final class PublicProjectListingQueryService {
    private final ProjectPublicReadModel projects;
    private final ProjectPublicTagMapper tags;
    private final ProjectPublicRouteFactory routeFactory;

    public PublicProjectListingQueryService(
            ProjectPublicReadModel projects,
            ProjectTagVocabularyPort tags,
            ProjectPublicRouteFactory routeFactory) {
        this.projects = Objects.requireNonNull(projects, "projects");
        this.tags = new ProjectPublicTagMapper(Objects.requireNonNull(tags, "tags"));
        this.routeFactory = Objects.requireNonNull(routeFactory, "routeFactory");
    }

    public List<PublicProjectCardView> list(ContentLanguage language) {
        Objects.requireNonNull(language, "language");
        return projects.listListedProjects(language).stream()
                .map(row -> new PublicProjectCardView(
                        row.title(),
                        row.summary(),
                        row.slug(),
                        routeFactory.publicUrl(language, ProjectSlug.of(row.slug())).value(),
                        row.technologies(),
                        tags.activeTags(row.tagIds())))
                .toList();
    }

    public List<PublicProjectCardView> list(String language) {
        Objects.requireNonNull(language, "language");
        return list(ContentLanguage.valueOf(language));
    }
}
