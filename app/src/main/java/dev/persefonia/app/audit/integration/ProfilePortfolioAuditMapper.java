package dev.persefonia.app.audit.integration;

import static dev.persefonia.app.audit.integration.AdminAuditCommandFactory.metadata;

import dev.persefonia.audit.application.command.AppendAuditMetadataCommand;
import dev.persefonia.audit.application.command.AppendAuditRecordCommand;
import dev.persefonia.profileportfolio.application.command.ActiveCvUpdateResult;
import dev.persefonia.profileportfolio.application.command.CreateProjectCommand;
import dev.persefonia.profileportfolio.application.command.PersonalProfileUpdateResult;
import dev.persefonia.profileportfolio.application.command.ProjectMutationResult;
import dev.persefonia.profileportfolio.application.command.SitePresentationSettingsUpdateResult;
import dev.persefonia.profileportfolio.application.command.UpdateActiveCvCommand;
import dev.persefonia.profileportfolio.application.command.UpdateProjectCommand;
import dev.persefonia.profileportfolio.application.command.UpdateSitePresentationSettingsCommand;
import dev.persefonia.profileportfolio.application.command.UpsertActivePersonalProfileCommand;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class ProfilePortfolioAuditMapper {
    private final AdminAuditCommandFactory factory;

    public ProfilePortfolioAuditMapper(AdminAuditCommandFactory factory) {
        this.factory = Objects.requireNonNull(factory, "factory");
    }

    public AppendAuditRecordCommand projectCreated(CreateProjectCommand command, ProjectMutationResult result) {
        return project(
                AuditActionCatalog.PROJECT_CREATED,
                command.actor().identityRef(),
                result.projectId(),
                command.status(),
                command.visibility(),
                command.featured(),
                command.tagIds().size(),
                command.localizations().size(),
                command.technologies().size(),
                command.links().size());
    }

    public AppendAuditRecordCommand projectUpdated(UpdateProjectCommand command, ProjectMutationResult result) {
        return project(
                AuditActionCatalog.PROJECT_UPDATED,
                command.actor().identityRef(),
                result.projectId(),
                command.status(),
                command.visibility(),
                command.featured(),
                command.tagIds().size(),
                command.localizations().size(),
                command.technologies().size(),
                command.links().size());
    }

    public AppendAuditRecordCommand profileUpserted(
            UpsertActivePersonalProfileCommand command, PersonalProfileUpdateResult result) {
        return factory.admin(
                AuditActionCatalog.PROFILE_UPSERTED,
                command.actor().identityRef(),
                AuditEntityCatalog.PERSONAL_PROFILE,
                result.profileId(),
                List.of(),
                List.of(
                        metadata("created", result.created()),
                        metadata("localization_count", command.localizations().size()),
                        metadata("external_link_count", command.externalLinks().size())));
    }

    public AppendAuditRecordCommand siteSettingsUpdated(
            UpdateSitePresentationSettingsCommand command, SitePresentationSettingsUpdateResult result) {
        return factory.admin(
                AuditActionCatalog.SITE_SETTINGS_UPDATED,
                command.actor().identityRef(),
                AuditEntityCatalog.SITE_PRESENTATION_SETTINGS,
                result.id(),
                List.of(),
                List.of(
                        metadata("default_language", command.defaultLanguage()),
                        metadata("supported_language_count", command.supportedLanguages().size()),
                        metadata("default_theme", command.defaultTheme()),
                        metadata("show_featured_projects", command.showFeaturedProjects()),
                        metadata("show_latest_writing", command.showLatestWriting()),
                        metadata("show_research_highlights", command.showResearchHighlights()),
                        metadata("featured_project_limit", command.featuredProjectLimit()),
                        metadata("latest_writing_limit", command.latestWritingLimit())));
    }

    public AppendAuditRecordCommand activeCvUpdated(UpdateActiveCvCommand command, ActiveCvUpdateResult result) {
        long selectionCount = command.selections().stream()
                .filter(selection -> selection.mediaAssetId() != null && !selection.mediaAssetId().isBlank())
                .count();
        return factory.admin(
                AuditActionCatalog.CV_ACTIVE_UPDATED,
                command.actor().identityRef(),
                AuditEntityCatalog.ACTIVE_CV_PROFILE,
                result.profileId(),
                List.of(),
                List.of(metadata("selection_count", selectionCount)));
    }

    private AppendAuditRecordCommand project(
            String action,
            UUID actorId,
            UUID projectId,
            String status,
            String visibility,
            boolean featured,
            int tagCount,
            int localizationCount,
            int technologyCount,
            int linkCount) {
        List<AppendAuditMetadataCommand> values = List.of(
                metadata("status", status),
                metadata("visibility", visibility),
                metadata("featured", featured),
                metadata("tag_count", tagCount),
                metadata("localization_count", localizationCount),
                metadata("technology_count", technologyCount),
                metadata("link_count", linkCount));
        return factory.admin(action, actorId, AuditEntityCatalog.PROJECT, projectId, List.of(), values);
    }
}
