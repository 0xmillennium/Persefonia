package dev.persefonia.app.profileportfolio.migration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PortfolioProjectSchemaTest {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-06-16T10:00:00Z");

    @BeforeAll
    static void migrateDatabase() {
        PortfolioMigrationDatabase.start();
        PortfolioMigrationDatabase.cleanMigrate();
    }

    @BeforeEach
    void truncateProjects() throws SQLException {
        PortfolioMigrationDatabase.truncatePortfolioMutableTables();
    }

    @Test
    void featuredProjectWithNonPublicVisibilityIsRejected() {
        assertThatThrownBy(() -> insertProject(UUID.randomUUID(), "ACTIVE", "PRIVATE", true))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void featuredArchivedProjectIsRejected() {
        assertThatThrownBy(() -> insertProject(UUID.randomUUID(), "ARCHIVED", "PUBLIC", true))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void duplicateProjectLocalizationLanguageIsRejected() throws SQLException {
        UUID projectId = insertProject(UUID.randomUUID(), "ACTIVE", "PUBLIC", false);
        insertLocalization(UUID.randomUUID(), projectId, "TR", "same-language");

        assertThatThrownBy(() -> insertLocalization(UUID.randomUUID(), projectId, "TR", "other-language"))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void duplicateProjectLocalizationLanguageSlugIsRejected() throws SQLException {
        UUID first = insertProject(UUID.randomUUID(), "ACTIVE", "PUBLIC", false);
        UUID second = insertProject(UUID.randomUUID(), "ACTIVE", "PUBLIC", false);
        insertLocalization(UUID.randomUUID(), first, "TR", "same-slug");

        assertThatThrownBy(() -> insertLocalization(UUID.randomUUID(), second, "TR", "same-slug"))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void invalidProjectSlugIsRejected() throws SQLException {
        UUID projectId = insertProject(UUID.randomUUID(), "ACTIVE", "PUBLIC", false);

        assertThatThrownBy(() -> insertLocalization(UUID.randomUUID(), projectId, "TR", "Bad Slug"))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void duplicateProjectTechnologyNormalizedNameCategoryIsRejected() throws SQLException {
        UUID projectId = insertProject(UUID.randomUUID(), "ACTIVE", "PUBLIC", false);
        insertTechnology(UUID.randomUUID(), projectId, "java", "LANGUAGE", 1);

        assertThatThrownBy(() -> insertTechnology(UUID.randomUUID(), projectId, "java", "LANGUAGE", 2))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void duplicateProjectLinkSortOrderIsRejected() throws SQLException {
        UUID projectId = insertProject(UUID.randomUUID(), "ACTIVE", "PUBLIC", false);
        insertLink(UUID.randomUUID(), projectId, 1);

        assertThatThrownBy(() -> insertLink(UUID.randomUUID(), projectId, 1))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void duplicateProjectCaseStudySectionTypeIsRejected() throws SQLException {
        UUID localizationId = insertLocalization(UUID.randomUUID(), insertProject(UUID.randomUUID(), "ACTIVE", "PUBLIC", false), "TR", "case-study");
        insertSection(UUID.randomUUID(), localizationId, "PROBLEM", 1);

        assertThatThrownBy(() -> insertSection(UUID.randomUUID(), localizationId, "PROBLEM", 2))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void duplicateProjectCaseStudySectionSortOrderIsRejected() throws SQLException {
        UUID localizationId = insertLocalization(UUID.randomUUID(), insertProject(UUID.randomUUID(), "ACTIVE", "PUBLIC", false), "TR", "case-study-order");
        insertSection(UUID.randomUUID(), localizationId, "PROBLEM", 1);

        assertThatThrownBy(() -> insertSection(UUID.randomUUID(), localizationId, "RESULT", 1))
                .isInstanceOf(SQLException.class);
    }

    private static UUID insertProject(UUID id, String status, String visibility, boolean featured) throws SQLException {
        PortfolioSql.update("""
                INSERT INTO portfolio.projects (
                    id, status, visibility, featured, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, 0)
                """, id, status, visibility, featured, NOW, NOW);
        return id;
    }

    private static UUID insertLocalization(UUID id, UUID projectId, String language, String slug) throws SQLException {
        PortfolioSql.update("""
                INSERT INTO portfolio.project_localizations (id, project_id, language, slug, title, summary)
                VALUES (?, ?, ?, ?, 'Title', 'Summary')
                """, id, projectId, language, slug);
        return id;
    }

    private static void insertTechnology(UUID id, UUID projectId, String normalizedName, String category, int sortOrder)
            throws SQLException {
        PortfolioSql.update("""
                INSERT INTO portfolio.project_technologies (
                    id, project_id, name, normalized_name, category, sort_order
                ) VALUES (?, ?, 'Java', ?, ?, ?)
                """, id, projectId, normalizedName, category, sortOrder);
    }

    private static void insertLink(UUID id, UUID projectId, int sortOrder) throws SQLException {
        PortfolioSql.update("""
                INSERT INTO portfolio.project_links (id, project_id, label, url, link_type, sort_order)
                VALUES (?, ?, 'Demo', 'https://example.test', 'DEMO', ?)
                """, id, projectId, sortOrder);
    }

    private static void insertSection(UUID id, UUID localizationId, String type, int sortOrder) throws SQLException {
        PortfolioSql.update("""
                INSERT INTO portfolio.project_case_study_sections (
                    id, project_localization_id, type, body, sort_order
                ) VALUES (?, ?, ?, 'Body', ?)
                """, id, localizationId, type, sortOrder);
    }
}
