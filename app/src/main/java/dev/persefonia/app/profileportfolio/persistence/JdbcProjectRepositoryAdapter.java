package dev.persefonia.app.profileportfolio.persistence;

import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.common.TagId;
import dev.persefonia.profileportfolio.domain.project.Project;
import dev.persefonia.profileportfolio.domain.project.ProjectCaseStudySection;
import dev.persefonia.profileportfolio.domain.project.ProjectId;
import dev.persefonia.profileportfolio.domain.project.ProjectLink;
import dev.persefonia.profileportfolio.domain.project.ProjectLocalization;
import dev.persefonia.profileportfolio.domain.project.ProjectRepository;
import dev.persefonia.profileportfolio.domain.project.ProjectSlug;
import dev.persefonia.profileportfolio.domain.project.ProjectTechnology;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class JdbcProjectRepositoryAdapter implements ProjectRepository {
    private final ObjectProvider<NamedParameterJdbcTemplate> jdbc;
    private final ObjectProvider<TransactionTemplate> transactions;
    private final ProjectPersistenceMapper mapper = new ProjectPersistenceMapper();

    JdbcProjectRepositoryAdapter(
            ObjectProvider<NamedParameterJdbcTemplate> jdbc,
            ObjectProvider<TransactionTemplate> transactions) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    @Override
    public Project save(Project project) {
        Objects.requireNonNull(project, "project");
        return transactionTemplate().execute(status -> {
            Optional<Long> currentVersion = currentVersion(project.id().value());
            if (currentVersion.isEmpty()) {
                insertProject(project);
            } else {
                updateProject(project, currentVersion.get());
            }
            replaceChildren(project);
            return findById(project.id()).orElseThrow(() -> new PortfolioPersistenceException(
                    "Saved project could not be reloaded: " + project.id().value()));
        });
    }

    @Override
    public Optional<Project> findById(ProjectId id) {
        Objects.requireNonNull(id, "id");
        return loadProject("projects.id = :id", Map.of("id", id.value()));
    }

    @Override
    public Optional<Project> findBySlug(ProjectSlug slug, ContentLanguage language) {
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(language, "language");
        return loadProject("""
                projects.id = (
                    SELECT project_id
                    FROM portfolio.project_localizations
                    WHERE slug = :slug AND language = :language
                )
                """, Map.of("slug", slug.value(), "language", language.name()));
    }

    @Override
    public boolean existsSlug(ProjectSlug slug, ContentLanguage language) {
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(language, "language");
        Long count = jdbc().queryForObject("""
                SELECT count(*)
                FROM portfolio.project_localizations
                WHERE slug = :slug AND language = :language
                """, Map.of("slug", slug.value(), "language", language.name()), Long.class);
        return count != null && count > 0;
    }

    private Optional<Long> currentVersion(UUID id) {
        return jdbc().query("""
                SELECT version
                FROM portfolio.projects
                WHERE id = :id
                """, Map.of("id", id), (resultSet, rowNumber) -> resultSet.getLong("version")).stream().findFirst();
    }

    private void insertProject(Project project) {
        jdbc().update("""
                INSERT INTO portfolio.projects (
                    id, status, visibility, featured, sort_order, cover_asset_id,
                    created_at, updated_at, version
                ) VALUES (
                    :id, :status, :visibility, :featured, :sortOrder, :coverAssetId,
                    :createdAt, :updatedAt, :version
                )
                """, parameters(project));
    }

    private void updateProject(Project project, long expectedVersion) {
        if (project.version().value() <= expectedVersion) {
            throw new OptimisticLockingFailureException("Project save is stale for id " + project.id().value());
        }
        int updated = jdbc().update("""
                UPDATE portfolio.projects
                SET status = :status,
                    visibility = :visibility,
                    featured = :featured,
                    sort_order = :sortOrder,
                    cover_asset_id = :coverAssetId,
                    updated_at = :updatedAt,
                    version = :version
                WHERE id = :id AND version = :expectedVersion
                """, parameters(project).addValue("expectedVersion", expectedVersion));
        if (updated != 1) {
            throw new OptimisticLockingFailureException("Project save is stale for id " + project.id().value());
        }
    }

    private MapSqlParameterSource parameters(Project project) {
        return new MapSqlParameterSource()
                .addValue("id", project.id().value())
                .addValue("status", project.status().name())
                .addValue("visibility", project.visibility().name())
                .addValue("featured", project.featured())
                .addValue("sortOrder", project.sortOrder().map(sort -> sort.value()).orElse(null))
                .addValue("coverAssetId", project.coverAssetId().map(asset -> asset.value()).orElse(null))
                .addValue("createdAt", Timestamp.from(project.createdAt()))
                .addValue("updatedAt", Timestamp.from(project.updatedAt()))
                .addValue("version", project.version().value());
    }

    private void replaceChildren(Project project) {
        jdbc().update("""
                DELETE FROM portfolio.project_localizations
                WHERE project_id = :projectId
                """, Map.of("projectId", project.id().value()));
        jdbc().update("""
                DELETE FROM portfolio.project_technologies
                WHERE project_id = :projectId
                """, Map.of("projectId", project.id().value()));
        jdbc().update("""
                DELETE FROM portfolio.project_links
                WHERE project_id = :projectId
                """, Map.of("projectId", project.id().value()));
        jdbc().update("""
                DELETE FROM portfolio.project_tags
                WHERE project_id = :projectId
                """, Map.of("projectId", project.id().value()));
        insertLocalizations(project);
        insertTechnologies(project);
        insertLinks(project);
        insertTags(project);
    }

    private void insertLocalizations(Project project) {
        MapSqlParameterSource[] localizationBatch = project.localizations().stream()
                .map(localization -> new MapSqlParameterSource()
                        .addValue("id", localization.id().value())
                        .addValue("projectId", project.id().value())
                        .addValue("language", localization.language().name())
                        .addValue("slug", localization.slug().value())
                        .addValue("title", localization.title().value())
                        .addValue("summary", localization.summary().value()))
                .toArray(MapSqlParameterSource[]::new);
        jdbc().batchUpdate("""
                INSERT INTO portfolio.project_localizations (
                    id, project_id, language, slug, title, summary
                ) VALUES (
                    :id, :projectId, :language, :slug, :title, :summary
                )
                """, localizationBatch);
        for (ProjectLocalization localization : project.localizations()) {
            insertSections(localization);
        }
    }

    private void insertSections(ProjectLocalization localization) {
        MapSqlParameterSource[] batch = localization.sections().stream()
                .map(section -> new MapSqlParameterSource()
                        .addValue("id", section.id().value())
                        .addValue("projectLocalizationId", localization.id().value())
                        .addValue("type", section.type().name())
                        .addValue("body", section.body().value())
                        .addValue("sortOrder", section.sortOrder().value()))
                .toArray(MapSqlParameterSource[]::new);
        jdbc().batchUpdate("""
                INSERT INTO portfolio.project_case_study_sections (
                    id, project_localization_id, type, body, sort_order
                ) VALUES (
                    :id, :projectLocalizationId, :type, :body, :sortOrder
                )
                """, batch);
    }

    private void insertTechnologies(Project project) {
        MapSqlParameterSource[] batch = project.technologies().stream()
                .map(technology -> new MapSqlParameterSource()
                        .addValue("id", technology.id().value())
                        .addValue("projectId", project.id().value())
                        .addValue("name", technology.name().value())
                        .addValue("normalizedName", technology.normalizedName().value())
                        .addValue("category", technology.category().name())
                        .addValue("sortOrder", technology.sortOrder().value()))
                .toArray(MapSqlParameterSource[]::new);
        jdbc().batchUpdate("""
                INSERT INTO portfolio.project_technologies (
                    id, project_id, name, normalized_name, category, sort_order
                ) VALUES (
                    :id, :projectId, :name, :normalizedName, :category, :sortOrder
                )
                """, batch);
    }

    private void insertLinks(Project project) {
        MapSqlParameterSource[] batch = project.links().stream()
                .map(link -> new MapSqlParameterSource()
                        .addValue("id", link.id().value())
                        .addValue("projectId", project.id().value())
                        .addValue("label", link.label().value())
                        .addValue("url", link.url().value())
                        .addValue("linkType", link.linkType().name())
                        .addValue("sortOrder", link.sortOrder().value()))
                .toArray(MapSqlParameterSource[]::new);
        jdbc().batchUpdate("""
                INSERT INTO portfolio.project_links (
                    id, project_id, label, url, link_type, sort_order
                ) VALUES (
                    :id, :projectId, :label, :url, :linkType, :sortOrder
                )
                """, batch);
    }

    private void insertTags(Project project) {
        Instant assignedAt = project.updatedAt();
        MapSqlParameterSource[] batch = project.tagIds().stream()
                .map(tagId -> new MapSqlParameterSource()
                        .addValue("projectId", project.id().value())
                        .addValue("tagId", tagId.value())
                        .addValue("assignedAt", Timestamp.from(assignedAt)))
                .toArray(MapSqlParameterSource[]::new);
        jdbc().batchUpdate("""
                INSERT INTO portfolio.project_tags (project_id, tag_id, assigned_at)
                VALUES (:projectId, :tagId, :assignedAt)
                """, batch);
    }

    private Optional<Project> loadProject(String whereClause, Map<String, Object> params) {
        String sql = """
                SELECT projects.id, projects.status, projects.visibility, projects.featured,
                       projects.sort_order, projects.cover_asset_id, projects.created_at,
                       projects.updated_at, projects.version
                FROM portfolio.projects projects
                WHERE %s
                """.formatted(whereClause);
        List<Project> rows = jdbc().query(sql, params, (resultSet, rowNumber) -> {
            UUID projectId = resultSet.getObject("id", UUID.class);
            return mapper.toDomain(
                    resultSet,
                    loadTagIds(projectId),
                    loadTechnologies(projectId),
                    loadLinks(projectId),
                    loadLocalizations(projectId));
        });
        return rows.stream().findFirst();
    }

    private Set<TagId> loadTagIds(UUID projectId) {
        return new LinkedHashSet<>(jdbc().query("""
                SELECT tag_id
                FROM portfolio.project_tags
                WHERE project_id = :projectId
                ORDER BY assigned_at, tag_id
                """, Map.of("projectId", projectId), (resultSet, rowNumber) ->
                TagId.from(resultSet.getObject("tag_id", UUID.class))));
    }

    private List<ProjectTechnology> loadTechnologies(UUID projectId) {
        return jdbc().query("""
                SELECT id, name, normalized_name, category, sort_order
                FROM portfolio.project_technologies
                WHERE project_id = :projectId
                ORDER BY sort_order
                """, Map.of("projectId", projectId), (resultSet, rowNumber) -> mapper.technology(resultSet));
    }

    private List<ProjectLink> loadLinks(UUID projectId) {
        return jdbc().query("""
                SELECT id, label, url, link_type, sort_order
                FROM portfolio.project_links
                WHERE project_id = :projectId
                ORDER BY sort_order
                """, Map.of("projectId", projectId), (resultSet, rowNumber) -> mapper.link(resultSet));
    }

    private List<ProjectLocalization> loadLocalizations(UUID projectId) {
        return jdbc().query("""
                SELECT id, language, slug, title, summary
                FROM portfolio.project_localizations
                WHERE project_id = :projectId
                ORDER BY language
                """, Map.of("projectId", projectId), (resultSet, rowNumber) -> {
            UUID localizationId = resultSet.getObject("id", UUID.class);
            return mapper.localization(resultSet, loadSections(localizationId));
        });
    }

    private List<ProjectCaseStudySection> loadSections(UUID localizationId) {
        return jdbc().query("""
                SELECT id, type, body, sort_order
                FROM portfolio.project_case_study_sections
                WHERE project_localization_id = :localizationId
                ORDER BY sort_order
                """, Map.of("localizationId", localizationId), (resultSet, rowNumber) -> mapper.section(resultSet));
    }

    private NamedParameterJdbcTemplate jdbc() {
        NamedParameterJdbcTemplate available = jdbc.getIfAvailable();
        if (available == null) {
            throw new PortfolioPersistenceException("JDBC project repository is not available.");
        }
        return available;
    }

    private TransactionTemplate transactionTemplate() {
        TransactionTemplate available = transactions.getIfAvailable();
        if (available == null) {
            throw new PortfolioPersistenceException("JDBC transaction infrastructure is not available.");
        }
        return available;
    }
}
