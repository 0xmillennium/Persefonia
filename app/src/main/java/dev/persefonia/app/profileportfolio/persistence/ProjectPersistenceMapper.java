package dev.persefonia.app.profileportfolio.persistence;

import dev.persefonia.profileportfolio.domain.common.AssetId;
import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.common.SortOrder;
import dev.persefonia.profileportfolio.domain.common.TagId;
import dev.persefonia.profileportfolio.domain.common.Version;
import dev.persefonia.profileportfolio.domain.common.ExternalUrl;
import dev.persefonia.profileportfolio.domain.common.LinkLabel;
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
import dev.persefonia.profileportfolio.domain.project.ProjectSlug;
import dev.persefonia.profileportfolio.domain.project.ProjectStatus;
import dev.persefonia.profileportfolio.domain.project.ProjectSummary;
import dev.persefonia.profileportfolio.domain.project.ProjectTechnology;
import dev.persefonia.profileportfolio.domain.project.ProjectTechnologyId;
import dev.persefonia.profileportfolio.domain.project.ProjectTitle;
import dev.persefonia.profileportfolio.domain.project.ProjectVisibility;
import dev.persefonia.profileportfolio.domain.project.TechnologyCategory;
import dev.persefonia.profileportfolio.domain.project.TechnologyName;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class ProjectPersistenceMapper {
    record ProjectRow(
            ProjectId id,
            ProjectStatus status,
            ProjectVisibility visibility,
            boolean featured,
            SortOrder sortOrder,
            AssetId coverAssetId,
            Instant createdAt,
            Instant updatedAt,
            Version version) {}

    ProjectRow projectRow(ResultSet resultSet) throws SQLException {
        return new ProjectRow(
                ProjectId.from(resultSet.getObject("id", UUID.class)),
                ProjectStatus.valueOf(resultSet.getString("status")),
                ProjectVisibility.valueOf(resultSet.getString("visibility")),
                resultSet.getBoolean("featured"),
                nullableInteger(resultSet, "sort_order"),
                nullable(resultSet.getObject("cover_asset_id", UUID.class), AssetId::from),
                timestamp(resultSet, "created_at"),
                timestamp(resultSet, "updated_at"),
                Version.of(resultSet.getLong("version")));
    }

    Project toDomain(
            ProjectRow row,
            Set<TagId> tagIds,
            List<ProjectTechnology> technologies,
            List<ProjectLink> links,
            List<ProjectLocalization> localizations) {
        return Project.rehydrate(
                row.id(),
                row.status(),
                row.visibility(),
                row.featured(),
                row.sortOrder(),
                row.coverAssetId(),
                tagIds,
                technologies,
                links,
                localizations,
                row.createdAt(),
                row.updatedAt(),
                row.version());
    }

    record LocalizationRow(
            ProjectLocalizationId id,
            ContentLanguage language,
            ProjectSlug slug,
            ProjectTitle title,
            ProjectSummary summary) {}

    LocalizationRow localizationRow(ResultSet resultSet) throws SQLException {
        return new LocalizationRow(
                ProjectLocalizationId.from(resultSet.getObject("id", UUID.class)),
                ContentLanguage.valueOf(resultSet.getString("language")),
                ProjectSlug.of(resultSet.getString("slug")),
                ProjectTitle.of(resultSet.getString("title")),
                ProjectSummary.of(resultSet.getString("summary")));
    }

    ProjectLocalization localization(LocalizationRow row, List<ProjectCaseStudySection> sections) {
        return new ProjectLocalization(
                row.id(),
                row.language(),
                row.slug(),
                row.title(),
                row.summary(),
                sections);
    }

    ProjectTechnology technology(ResultSet resultSet) throws SQLException {
        return new ProjectTechnology(
                ProjectTechnologyId.from(resultSet.getObject("id", UUID.class)),
                TechnologyName.of(resultSet.getString("name")),
                NormalizedTechnologyName.of(resultSet.getString("normalized_name")),
                TechnologyCategory.valueOf(resultSet.getString("category")),
                SortOrder.of(resultSet.getInt("sort_order")));
    }

    ProjectLink link(ResultSet resultSet) throws SQLException {
        return new ProjectLink(
                ProjectLinkId.from(resultSet.getObject("id", UUID.class)),
                LinkLabel.of(resultSet.getString("label")),
                ExternalUrl.of(resultSet.getString("url")),
                ProjectLinkType.valueOf(resultSet.getString("link_type")),
                SortOrder.of(resultSet.getInt("sort_order")));
    }

    ProjectCaseStudySection section(ResultSet resultSet) throws SQLException {
        return new ProjectCaseStudySection(
                ProjectCaseStudySectionId.from(resultSet.getObject("id", UUID.class)),
                CaseStudySectionType.valueOf(resultSet.getString("type")),
                CaseStudyText.of(resultSet.getString("body")),
                SortOrder.of(resultSet.getInt("sort_order")));
    }

    private Instant timestamp(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getTimestamp(column).toInstant();
    }

    private SortOrder nullableInteger(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : SortOrder.of(value);
    }

    private <T> T nullable(UUID value, java.util.function.Function<UUID, T> mapper) {
        return value == null ? null : mapper.apply(value);
    }
}
