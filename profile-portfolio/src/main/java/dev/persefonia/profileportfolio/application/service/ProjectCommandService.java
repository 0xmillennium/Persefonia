package dev.persefonia.profileportfolio.application.service;

import dev.persefonia.profileportfolio.application.authorization.PortfolioCommandAuthorizationPolicy;
import dev.persefonia.profileportfolio.application.command.CreateProjectCommand;
import dev.persefonia.profileportfolio.application.command.ProjectCaseStudySectionInput;
import dev.persefonia.profileportfolio.application.command.ProjectLinkInput;
import dev.persefonia.profileportfolio.application.command.ProjectLocalizationInput;
import dev.persefonia.profileportfolio.application.command.ProjectMutationResult;
import dev.persefonia.profileportfolio.application.command.ProjectTechnologyInput;
import dev.persefonia.profileportfolio.application.command.UpdateProjectCommand;
import dev.persefonia.profileportfolio.application.exception.ProjectApplicationException;
import dev.persefonia.profileportfolio.application.exception.ProjectCommandRejectedException;
import dev.persefonia.profileportfolio.application.exception.ProjectNotFoundException;
import dev.persefonia.profileportfolio.application.exception.SitePresentationSettingsNotInitializedException;
import dev.persefonia.profileportfolio.application.port.ProjectTagVocabularyPort;
import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.common.ExternalUrl;
import dev.persefonia.profileportfolio.domain.common.LinkLabel;
import dev.persefonia.profileportfolio.domain.common.PortfolioValidationException;
import dev.persefonia.profileportfolio.domain.common.SortOrder;
import dev.persefonia.profileportfolio.domain.common.TagId;
import dev.persefonia.profileportfolio.domain.project.CaseStudySectionType;
import dev.persefonia.profileportfolio.domain.project.CaseStudyText;
import dev.persefonia.profileportfolio.domain.project.NormalizedTechnologyName;
import dev.persefonia.profileportfolio.domain.project.Project;
import dev.persefonia.profileportfolio.domain.project.ProjectCaseStudySection;
import dev.persefonia.profileportfolio.domain.project.ProjectCaseStudySectionId;
import dev.persefonia.profileportfolio.domain.project.ProjectId;
import dev.persefonia.profileportfolio.domain.project.ProjectLink;
import dev.persefonia.profileportfolio.domain.project.ProjectLinkId;
import dev.persefonia.profileportfolio.domain.project.ProjectLinkType;
import dev.persefonia.profileportfolio.domain.project.ProjectLocalization;
import dev.persefonia.profileportfolio.domain.project.ProjectLocalizationId;
import dev.persefonia.profileportfolio.domain.project.ProjectRepository;
import dev.persefonia.profileportfolio.domain.project.ProjectSlug;
import dev.persefonia.profileportfolio.domain.project.ProjectStatus;
import dev.persefonia.profileportfolio.domain.project.ProjectSummary;
import dev.persefonia.profileportfolio.domain.project.ProjectTechnology;
import dev.persefonia.profileportfolio.domain.project.ProjectTechnologyId;
import dev.persefonia.profileportfolio.domain.project.ProjectTitle;
import dev.persefonia.profileportfolio.domain.project.ProjectVisibility;
import dev.persefonia.profileportfolio.domain.project.TechnologyCategory;
import dev.persefonia.profileportfolio.domain.project.TechnologyName;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettingsRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ProjectCommandService {
    private final ProjectRepository projects;
    private final SitePresentationSettingsRepository settings;
    private final ProjectTagVocabularyPort tags;
    private final PortfolioCommandAuthorizationPolicy authorization;

    public ProjectCommandService(
            ProjectRepository projects,
            SitePresentationSettingsRepository settings,
            ProjectTagVocabularyPort tags,
            PortfolioCommandAuthorizationPolicy authorization) {
        this.projects = Objects.requireNonNull(projects, "projects");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.tags = Objects.requireNonNull(tags, "tags");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
    }

    public ProjectMutationResult create(CreateProjectCommand command) {
        Objects.requireNonNull(command, "command");
        authorization.requireOwner(command.actor(), "portfolio.project.create");
        ContentLanguage defaultLanguage = defaultLanguage();
        Set<TagId> requestedTags = tagIds(command.tagIds());
        validateTags(Set.of(), requestedTags);
        try {
            List<ProjectLocalization> localizations = localizations(command.localizations());
            ProjectId projectId = ProjectId.newId();
            rejectDuplicateSlugs(projectId, localizations, true);
            Project project = Project.create(
                    projectId,
                    ProjectStatus.valueOf(command.status()),
                    ProjectVisibility.valueOf(command.visibility()),
                    command.featured(),
                    optionalSortOrder(command.sortOrder()),
                    null,
                    requestedTags,
                    technologies(command.technologies()),
                    links(command.links()),
                    localizations,
                    defaultLanguage,
                    command.requestedAt());
            Project saved = projects.save(project);
            return new ProjectMutationResult(saved.id().value(), true, saved.updatedAt(), saved.version().value());
        } catch (IllegalArgumentException | PortfolioValidationException exception) {
            throw new ProjectApplicationException("Project creation was rejected.", exception);
        }
    }

    public ProjectMutationResult update(UpdateProjectCommand command) {
        Objects.requireNonNull(command, "command");
        authorization.requireOwner(command.actor(), "portfolio.project.update");
        ContentLanguage defaultLanguage = defaultLanguage();
        ProjectId projectId = ProjectId.from(command.projectId());
        Project project = projects.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(command.projectId()));
        Set<TagId> requestedTags = tagIds(command.tagIds());
        validateTags(project.tagIds(), requestedTags);
        try {
            List<ProjectLocalization> localizations = localizations(command.localizations());
            rejectDuplicateSlugs(project.id(), localizations, false);
            project.updateDetails(
                    ProjectStatus.valueOf(command.status()),
                    ProjectVisibility.valueOf(command.visibility()),
                    command.featured(),
                    optionalSortOrder(command.sortOrder()),
                    requestedTags,
                    technologies(command.technologies()),
                    links(command.links()),
                    localizations,
                    defaultLanguage,
                    command.requestedAt());
            Project saved = projects.save(project);
            return new ProjectMutationResult(saved.id().value(), false, saved.updatedAt(), saved.version().value());
        } catch (IllegalArgumentException | PortfolioValidationException exception) {
            throw new ProjectApplicationException("Project update was rejected.", exception);
        }
    }

    private ContentLanguage defaultLanguage() {
        return settings.findCurrent()
                .orElseThrow(SitePresentationSettingsNotInitializedException::new)
                .defaultLanguage();
    }

    private void validateTags(
            Set<TagId> currentlyAssigned,
            Set<TagId> requested) {
        var validation = tags.validateAssignments(currentlyAssigned, requested);
        if (!validation.missingTagIds().isEmpty()) {
            throw new ProjectCommandRejectedException(
                    ProjectCommandRejectedException.Reason.MISSING_TAG,
                    "One or more requested project tags do not exist.");
        }
        if (!validation.newlyArchivedTagIds().isEmpty()) {
            throw new ProjectCommandRejectedException(
                    ProjectCommandRejectedException.Reason.ARCHIVED_TAG,
                    "Archived project tags cannot be newly assigned.");
        }
    }

    private void rejectDuplicateSlugs(ProjectId currentProjectId, List<ProjectLocalization> localizations, boolean creating) {
        for (ProjectLocalization localization : localizations) {
            var existing = projects.findBySlug(localization.slug(), localization.language());
            if (existing.isPresent() && (creating || !existing.get().id().equals(currentProjectId))) {
                throw new ProjectCommandRejectedException(
                        ProjectCommandRejectedException.Reason.DUPLICATE_SLUG,
                        "A project slug is already in use for " + localization.language().name() + ".");
            }
        }
    }

    private static List<ProjectLocalization> localizations(List<ProjectLocalizationInput> inputs) {
        return inputs.stream()
                .map(input -> new ProjectLocalization(
                        ProjectLocalizationId.newId(),
                        ContentLanguage.valueOf(input.language()),
                        ProjectSlug.of(input.slug()),
                        ProjectTitle.of(input.title()),
                        ProjectSummary.of(input.summary()),
                        input.sections().stream().map(ProjectCommandService::section).toList()))
                .toList();
    }

    private static ProjectCaseStudySection section(ProjectCaseStudySectionInput input) {
        return new ProjectCaseStudySection(
                ProjectCaseStudySectionId.newId(),
                CaseStudySectionType.valueOf(input.type()),
                CaseStudyText.of(input.body()),
                SortOrder.of(input.sortOrder()));
    }

    private static List<ProjectTechnology> technologies(List<ProjectTechnologyInput> inputs) {
        return inputs.stream()
                .map(input -> new ProjectTechnology(
                        ProjectTechnologyId.newId(),
                        TechnologyName.of(input.name()),
                        NormalizedTechnologyName.of(input.name().trim().toLowerCase(Locale.ROOT)),
                        TechnologyCategory.valueOf(input.category()),
                        SortOrder.of(input.sortOrder())))
                .toList();
    }

    private static List<ProjectLink> links(List<ProjectLinkInput> inputs) {
        return inputs.stream()
                .map(input -> new ProjectLink(
                        ProjectLinkId.newId(),
                        LinkLabel.of(input.label()),
                        ExternalUrl.of(input.url()),
                        ProjectLinkType.valueOf(input.linkType()),
                        SortOrder.of(input.sortOrder())))
                .toList();
    }

    private static SortOrder optionalSortOrder(Integer value) {
        return value == null ? null : SortOrder.of(value);
    }

    private static Set<TagId> tagIds(Set<UUID> ids) {
        return ids.stream()
                .map(TagId::from)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
