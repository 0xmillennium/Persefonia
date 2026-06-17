package dev.persefonia.profileportfolio.application.service;

import dev.persefonia.profileportfolio.application.discovery.ProjectPublicRouteFactory;
import dev.persefonia.profileportfolio.application.port.ProjectPublicReadModel;
import dev.persefonia.profileportfolio.application.port.ProjectTagVocabularyPort;
import dev.persefonia.profileportfolio.application.query.PublicProjectDetailView;
import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.project.ProjectId;
import dev.persefonia.profileportfolio.domain.project.ProjectSlug;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class PublicProjectDetailQueryService {
    private final ProjectPublicReadModel projects;
    private final ProjectPublicTagMapper tags;
    private final ProjectPublicRouteFactory routeFactory;

    public PublicProjectDetailQueryService(
            ProjectPublicReadModel projects,
            ProjectTagVocabularyPort tags,
            ProjectPublicRouteFactory routeFactory) {
        this.projects = Objects.requireNonNull(projects, "projects");
        this.tags = new ProjectPublicTagMapper(Objects.requireNonNull(tags, "tags"));
        this.routeFactory = Objects.requireNonNull(routeFactory, "routeFactory");
    }

    public Optional<PublicProjectDetailView> find(UUID projectId, ContentLanguage language, ProjectSlug expectedSlug) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(expectedSlug, "expectedSlug");
        return projects.findDetail(ProjectId.from(projectId), language, expectedSlug)
                .map(row -> new PublicProjectDetailView(
                        row.title(),
                        row.summary(),
                        row.slug(),
                        routeFactory.publicUrl(language, ProjectSlug.of(row.slug())).value(),
                        row.technologies(),
                        row.links(),
                        tags.activeTags(row.tagIds()),
                        row.sections()));
    }

    public Optional<PublicProjectDetailView> find(UUID projectId, ContentLanguage language, String expectedSlug) {
        Objects.requireNonNull(expectedSlug, "expectedSlug");
        return find(projectId, language, ProjectSlug.of(expectedSlug));
    }

    public Optional<PublicProjectDetailView> find(UUID projectId, String language, String expectedSlug) {
        Objects.requireNonNull(language, "language");
        return find(projectId, ContentLanguage.valueOf(language), expectedSlug);
    }
}
