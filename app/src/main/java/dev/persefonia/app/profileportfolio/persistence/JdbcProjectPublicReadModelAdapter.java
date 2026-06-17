package dev.persefonia.app.profileportfolio.persistence;

import dev.persefonia.profileportfolio.application.port.ProjectPublicReadModel;
import dev.persefonia.profileportfolio.application.query.PublicProjectCaseStudySectionView;
import dev.persefonia.profileportfolio.application.query.PublicProjectLinkView;
import dev.persefonia.profileportfolio.application.query.PublicProjectTechnologyView;
import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.common.TagId;
import dev.persefonia.profileportfolio.domain.project.ProjectId;
import dev.persefonia.profileportfolio.domain.project.ProjectSlug;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcProjectPublicReadModelAdapter implements ProjectPublicReadModel {
    private final ObjectProvider<NamedParameterJdbcTemplate> jdbc;

    JdbcProjectPublicReadModelAdapter(ObjectProvider<NamedParameterJdbcTemplate> jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public List<ProjectSummaryRow> listListedProjects(ContentLanguage language) {
        Objects.requireNonNull(language, "language");
        return jdbc().query("""
                SELECT projects.id,
                       localizations.slug,
                       localizations.title,
                       localizations.summary
                FROM portfolio.projects projects
                JOIN portfolio.project_localizations localizations
                  ON localizations.project_id = projects.id
                 AND localizations.language = :language
                WHERE projects.visibility = 'PUBLIC'
                  AND projects.status <> 'ARCHIVED'
                ORDER BY projects.sort_order NULLS LAST, projects.updated_at DESC, projects.id
                """, Map.of("language", language.name()), (resultSet, rowNumber) -> {
            UUID projectId = resultSet.getObject("id", UUID.class);
            return new ProjectSummaryRow(
                    resultSet.getString("title"),
                    resultSet.getString("summary"),
                    resultSet.getString("slug"),
                    tagIds(projectId),
                    technologies(projectId));
        });
    }

    @Override
    public java.util.Optional<ProjectDetailRow> findDetail(
            ProjectId projectId,
            ContentLanguage language,
            ProjectSlug expectedSlug) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(expectedSlug, "expectedSlug");
        return jdbc().query("""
                SELECT localizations.id AS localization_id,
                       localizations.slug,
                       localizations.title,
                       localizations.summary
                FROM portfolio.projects projects
                JOIN portfolio.project_localizations localizations
                  ON localizations.project_id = projects.id
                 AND localizations.language = :language
                 AND localizations.slug = :slug
                WHERE projects.id = :projectId
                  AND projects.visibility IN ('PUBLIC', 'UNLISTED')
                  AND projects.status <> 'ARCHIVED'
                """, Map.of(
                "projectId", projectId.value(),
                "language", language.name(),
                "slug", expectedSlug.value()), (resultSet, rowNumber) -> {
            UUID localizationId = resultSet.getObject("localization_id", UUID.class);
            return new ProjectDetailRow(
                    resultSet.getString("title"),
                    resultSet.getString("summary"),
                    resultSet.getString("slug"),
                    tagIds(projectId.value()),
                    technologies(projectId.value()),
                    links(projectId.value()),
                    sections(localizationId));
        }).stream().findFirst();
    }

    @Override
    public List<ProjectSummaryRow> listFeaturedProjects(ContentLanguage language, int limit) {
        Objects.requireNonNull(language, "language");
        return jdbc().query("""
                SELECT projects.id,
                       localizations.slug,
                       localizations.title,
                       localizations.summary
                FROM portfolio.projects projects
                JOIN portfolio.project_localizations localizations
                  ON localizations.project_id = projects.id
                 AND localizations.language = :language
                WHERE projects.featured = true
                  AND projects.visibility = 'PUBLIC'
                  AND projects.status <> 'ARCHIVED'
                ORDER BY projects.sort_order NULLS LAST, projects.updated_at DESC, projects.id
                LIMIT :limit
                """, Map.of("language", language.name(), "limit", limit), (resultSet, rowNumber) -> {
            UUID projectId = resultSet.getObject("id", UUID.class);
            return new ProjectSummaryRow(
                    resultSet.getString("title"),
                    resultSet.getString("summary"),
                    resultSet.getString("slug"),
                    tagIds(projectId),
                    technologies(projectId));
        });
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

    private List<PublicProjectTechnologyView> technologies(UUID projectId) {
        return jdbc().query("""
                SELECT name, category
                FROM portfolio.project_technologies
                WHERE project_id = :projectId
                ORDER BY sort_order
                """, Map.of("projectId", projectId), (resultSet, rowNumber) ->
                new PublicProjectTechnologyView(
                        resultSet.getString("name"),
                        resultSet.getString("category")));
    }

    private List<PublicProjectLinkView> links(UUID projectId) {
        return jdbc().query("""
                SELECT label, url, link_type
                FROM portfolio.project_links
                WHERE project_id = :projectId
                ORDER BY sort_order
                """, Map.of("projectId", projectId), (resultSet, rowNumber) ->
                new PublicProjectLinkView(
                        resultSet.getString("label"),
                        resultSet.getString("url"),
                        resultSet.getString("link_type")));
    }

    private List<PublicProjectCaseStudySectionView> sections(UUID localizationId) {
        return jdbc().query("""
                SELECT type, body
                FROM portfolio.project_case_study_sections
                WHERE project_localization_id = :localizationId
                ORDER BY sort_order
                """, Map.of("localizationId", localizationId), (resultSet, rowNumber) ->
                new PublicProjectCaseStudySectionView(
                        resultSet.getString("type"),
                        resultSet.getString("body")));
    }

    private NamedParameterJdbcTemplate jdbc() {
        NamedParameterJdbcTemplate available = jdbc.getIfAvailable();
        if (available == null) {
            throw new PortfolioPersistenceException("JDBC project public read model is not available.");
        }
        return available;
    }
}
