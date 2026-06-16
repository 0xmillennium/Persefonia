package dev.persefonia.app.profileportfolio.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PortfolioSiteSettingsSchemaTest {
    private static final UUID SEEDED_SETTINGS_ID = UUID.fromString("00000000-0000-0000-0000-000000000701");

    @BeforeAll
    static void migrateDatabase() {
        PortfolioMigrationDatabase.start();
        PortfolioMigrationDatabase.cleanMigrate();
    }

    @Test
    void seedCreatesSettingsAndSupportedLanguagesOnly() throws SQLException {
        assertThat(PortfolioSql.count("""
                SELECT count(*)
                FROM portfolio.site_presentation_settings
                WHERE id = ?
                """, SEEDED_SETTINGS_ID)).isEqualTo(1);
        assertThat(PortfolioSql.strings("""
                SELECT language
                FROM portfolio.site_supported_languages
                WHERE settings_id = ?
                ORDER BY language
                """, SEEDED_SETTINGS_ID)).containsExactly("EN", "TR");
        assertThat(PortfolioSql.count("SELECT count(*) FROM portfolio.personal_profiles")).isZero();
        assertThat(PortfolioSql.count("SELECT count(*) FROM portfolio.projects")).isZero();
    }

    @Test
    void duplicateSingletonSettingsRowIsRejected() {
        assertThatThrownBy(() -> insertSettings(UUID.randomUUID(), "TR", 3, 5))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void defaultLanguageCheckRejectsInvalidLanguage() {
        assertThatThrownBy(() -> PortfolioSql.update("""
                UPDATE portfolio.site_presentation_settings
                SET default_language = 'DE'
                WHERE id = ?
                """, SEEDED_SETTINGS_ID)).isInstanceOf(SQLException.class);
    }

    @Test
    void featuredProjectLimitRejectsNonPositiveValues() {
        assertThatThrownBy(() -> PortfolioSql.update("""
                UPDATE portfolio.site_presentation_settings
                SET featured_project_limit = 0
                WHERE id = ?
                """, SEEDED_SETTINGS_ID)).isInstanceOf(SQLException.class);
    }

    @Test
    void latestWritingLimitRejectsNonPositiveValues() {
        assertThatThrownBy(() -> PortfolioSql.update("""
                UPDATE portfolio.site_presentation_settings
                SET latest_writing_limit = 0
                WHERE id = ?
                """, SEEDED_SETTINGS_ID)).isInstanceOf(SQLException.class);
    }

    private static void insertSettings(UUID id, String language, int featuredLimit, int writingLimit) throws SQLException {
        PortfolioSql.update("""
                INSERT INTO portfolio.site_presentation_settings (
                    id, singleton_key, site_name, default_language, default_theme,
                    show_featured_projects, show_latest_writing, show_research_highlights,
                    featured_project_limit, latest_writing_limit, updated_at, version
                ) VALUES (?, true, 'Persefonia', ?, 'SYSTEM', true, true, false, ?, ?, ?, 0)
                """, id, language, featuredLimit, writingLimit, OffsetDateTime.parse("2026-06-16T10:00:00Z"));
    }
}
