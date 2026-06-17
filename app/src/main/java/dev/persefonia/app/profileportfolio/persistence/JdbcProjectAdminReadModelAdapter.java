package dev.persefonia.app.profileportfolio.persistence;

import dev.persefonia.profileportfolio.application.port.ProjectAdminReadModel;
import dev.persefonia.profileportfolio.application.query.AdminProjectCaseStudySectionView;
import dev.persefonia.profileportfolio.application.query.AdminProjectLinkView;
import dev.persefonia.profileportfolio.application.query.AdminProjectListItem;
import dev.persefonia.profileportfolio.application.query.AdminProjectLocalizationView;
import dev.persefonia.profileportfolio.application.query.AdminProjectTechnologyView;
import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.common.TagId;
import dev.persefonia.profileportfolio.domain.project.ProjectId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcProjectAdminReadModelAdapter implements ProjectAdminReadModel {
    private final ObjectProvider<NamedParameterJdbcTemplate> jdbc;

    JdbcProjectAdminReadModelAdapter(ObjectProvider<NamedParameterJdbcTemplate> jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public List<AdminProjectListItem> list(ContentLanguage defaultLanguage) {
        Objects.requireNonNull(defaultLanguage, "defaultLanguage");
        return jdbc().query("""
                SELECT projects.id,
                       COALESCE(default_localization.title, fallback_localization.title, projects.id::text) AS title,
                       projects.status,
                       projects.visibility,
                       projects.featured,
                       projects.sort_order,
                       projects.updated_at
                FROM portfolio.projects projects
                LEFT JOIN portfolio.project_localizations default_localization
                    ON default_localization.project_id = projects.id
                   AND default_localization.language = :defaultLanguage
                LEFT JOIN LATERAL (
                    SELECT title
                    FROM portfolio.project_localizations localizations
                    WHERE localizations.project_id = projects.id
                    ORDER BY localizations.language
                    LIMIT 1
                ) fallback_localization ON true
                ORDER BY projects.updated_at DESC, projects.id
                """, Map.of("defaultLanguage", defaultLanguage.name()), (resultSet, rowNumber) ->
                new AdminProjectListItem(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("title"),
                        resultSet.getString("status"),
                        resultSet.getString("visibility"),
                        resultSet.getBoolean("featured"),
                        nullableInteger(resultSet, "sort_order"),
                        resultSet.getTimestamp("updated_at").toInstant()));
    }

    @Override
    public Optional<ProjectAdminDetails> findDetails(ProjectId projectId) {
        Objects.requireNonNull(projectId, "projectId");
        return jdbc().query("""
                SELECT id, status, visibility, featured, sort_order, updated_at, version
                FROM portfolio.projects
                WHERE id = :id
                """, Map.of("id", projectId.value()), (resultSet, rowNumber) -> new ProjectAdminDetails(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("status"),
                resultSet.getString("visibility"),
                resultSet.getBoolean("featured"),
                nullableInteger(resultSet, "sort_order"),
                tagIds(projectId.value()),
                localizations(projectId.value()),
                technologies(projectId.value()),
                links(projectId.value()),
                resultSet.getTimestamp("updated_at").toInstant(),
                resultSet.getLong("version"))).stream().findFirst();
    }

    private Set<TagId> tagIds(UUID projectId) {
        return new LinkedHashSet<>(jdbc().query("""
                SELECT tag_id
                FROM portfolio.project_tags
                WHERE project_id = :projectId
                ORDER BY assigned_at, tag_id
                """, Map.of("projectId", projectId), (resultSet, rowNumber) ->
                TagId.from(resultSet.getObject("tag_id", UUID.class))));
    }

    private List<AdminProjectLocalizationView> localizations(UUID projectId) {
        return jdbc().query("""
                SELECT id, language, slug, title, summary
                FROM portfolio.project_localizations
                WHERE project_id = :projectId
                ORDER BY language
                """, Map.of("projectId", projectId), (resultSet, rowNumber) -> {
            UUID localizationId = resultSet.getObject("id", UUID.class);
            return new AdminProjectLocalizationView(
                    resultSet.getString("language"),
                    resultSet.getString("slug"),
                    resultSet.getString("title"),
                    resultSet.getString("summary"),
                    sections(localizationId));
        });
    }

    private List<AdminProjectCaseStudySectionView> sections(UUID localizationId) {
        return jdbc().query("""
                SELECT type, body, sort_order
                FROM portfolio.project_case_study_sections
                WHERE project_localization_id = :localizationId
                ORDER BY sort_order
                """, Map.of("localizationId", localizationId), (resultSet, rowNumber) ->
                new AdminProjectCaseStudySectionView(
                        resultSet.getString("type"),
                        resultSet.getString("body"),
                        resultSet.getInt("sort_order")));
    }

    private List<AdminProjectTechnologyView> technologies(UUID projectId) {
        return jdbc().query("""
                SELECT name, category, sort_order
                FROM portfolio.project_technologies
                WHERE project_id = :projectId
                ORDER BY sort_order
                """, Map.of("projectId", projectId), (resultSet, rowNumber) ->
                new AdminProjectTechnologyView(
                        resultSet.getString("name"),
                        resultSet.getString("category"),
                        resultSet.getInt("sort_order")));
    }

    private List<AdminProjectLinkView> links(UUID projectId) {
        return jdbc().query("""
                SELECT label, url, link_type, sort_order
                FROM portfolio.project_links
                WHERE project_id = :projectId
                ORDER BY sort_order
                """, Map.of("projectId", projectId), (resultSet, rowNumber) ->
                new AdminProjectLinkView(
                        resultSet.getString("label"),
                        resultSet.getString("url"),
                        resultSet.getString("link_type"),
                        resultSet.getInt("sort_order")));
    }

    private NamedParameterJdbcTemplate jdbc() {
        NamedParameterJdbcTemplate available = jdbc.getIfAvailable();
        if (available == null) {
            throw new PortfolioPersistenceException("JDBC project admin read model is not available.");
        }
        return available;
    }

    private static Integer nullableInteger(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }
}
