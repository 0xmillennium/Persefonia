package dev.persefonia.profileportfolio.application.service;

import dev.persefonia.profileportfolio.application.exception.ProjectNotFoundException;
import dev.persefonia.profileportfolio.application.exception.SitePresentationSettingsNotInitializedException;
import dev.persefonia.profileportfolio.application.port.ProjectAdminReadModel;
import dev.persefonia.profileportfolio.application.port.ProjectTagVocabularyPort;
import dev.persefonia.profileportfolio.application.query.AdminProjectEditView;
import dev.persefonia.profileportfolio.application.query.AdminProjectFormOptions;
import dev.persefonia.profileportfolio.application.query.AdminProjectListItem;
import dev.persefonia.profileportfolio.application.query.AdminProjectTagView;
import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.common.TagId;
import dev.persefonia.profileportfolio.domain.project.ProjectId;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettingsRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ProjectAdminQueryService {
    private final ProjectAdminReadModel projects;
    private final SitePresentationSettingsRepository settings;
    private final ProjectTagVocabularyPort tags;

    public ProjectAdminQueryService(
            ProjectAdminReadModel projects,
            SitePresentationSettingsRepository settings,
            ProjectTagVocabularyPort tags) {
        this.projects = Objects.requireNonNull(projects, "projects");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.tags = Objects.requireNonNull(tags, "tags");
    }

    public List<AdminProjectListItem> list() {
        return projects.list(defaultLanguage());
    }

    public AdminProjectFormOptions formOptions() {
        return new AdminProjectFormOptions(defaultLanguage().name(), tags.findAssignableTags());
    }

    public AdminProjectEditView edit(java.util.UUID projectId) {
        ProjectAdminReadModel.ProjectAdminDetails project = projects.findDetails(ProjectId.from(projectId))
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
        ContentLanguage defaultLanguage = defaultLanguage();
        return new AdminProjectEditView(
                project.id(),
                project.status(),
                project.visibility(),
                project.featured(),
                project.sortOrder(),
                defaultLanguage.name(),
                assignedTags(project.tagIds()),
                project.localizations(),
                project.technologies(),
                project.links(),
                project.updatedAt(),
                project.version());
    }

    private List<AdminProjectTagView> assignedTags(Set<TagId> ids) {
        Map<TagId, AdminProjectTagView> found = tags.findByIds(ids).stream()
                .map(tag -> new AdminProjectTagView(tag.id(), tag.name(), tag.slug(), tag.archived()))
                .collect(Collectors.toMap(tag -> TagId.from(tag.id()), Function.identity()));
        return ids.stream()
                .map(found::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(AdminProjectTagView::name))
                .toList();
    }

    private ContentLanguage defaultLanguage() {
        return settings.findCurrent()
                .orElseThrow(SitePresentationSettingsNotInitializedException::new)
                .defaultLanguage();
    }

}
