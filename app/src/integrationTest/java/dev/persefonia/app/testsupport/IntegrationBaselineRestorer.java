package dev.persefonia.app.testsupport;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** Restores the canonical rows introduced by data-seeding Flyway migrations. */
final class IntegrationBaselineRestorer {
    private IntegrationBaselineRestorer() {}

    static void restore(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO portfolio.site_presentation_settings (
                        id, singleton_key, site_name, default_language, title_suffix,
                        default_meta_description, default_og_image_asset_id, default_theme,
                        show_featured_projects, show_latest_writing, show_research_highlights,
                        featured_project_limit, latest_writing_limit, updated_at, version
                    ) VALUES (
                        '00000000-0000-0000-0000-000000000701', true, 'Persefonia', 'TR', NULL,
                        NULL, NULL, 'SYSTEM', true, true, false, 3, 5, CURRENT_TIMESTAMP, 0
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO portfolio.site_supported_languages (settings_id, language)
                    VALUES
                        ('00000000-0000-0000-0000-000000000701', 'TR'),
                        ('00000000-0000-0000-0000-000000000701', 'EN')
                    """);
            statement.executeUpdate("""
                    INSERT INTO portfolio.active_cv_profiles (
                        id, singleton_key, created_at, updated_at, version
                    ) VALUES (
                        '00000000-0000-0000-0000-000000000801', true,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
                    )
                    """);
        }
    }
}
