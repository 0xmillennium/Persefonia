package dev.persefonia.app.profileportfolio.migration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.app.testsupport.SharedPostgresTestServer;
import java.sql.Connection;
import java.sql.ResultSet;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

class PortfolioSettingsUpgradeTest {
    @Test
    void upgradesExistingV21SingletonWithoutLosingValidValuesOrVersion() throws Exception {
        var database = SharedPostgresTestServer.migrationDatabase();
        database.start();
        try {
            var configuration = Flyway.configure()
                    .dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
                    .locations("classpath:db/migration")
                    .defaultSchema("operations")
                    .schemas("operations", "iam", "taxonomy", "publishing", "portfolio", "media",
                            "communication", "discovery", "integrity", "insights", "audit", "portability")
                    .createSchemas(true);
            configuration.target(MigrationVersion.fromVersion("21"));
            configuration.load().migrate();
            try (Connection connection = database.createConnection("");
                    var statement = connection.createStatement()) {
                statement.executeUpdate("""
                        UPDATE portfolio.site_presentation_settings
                        SET site_name = 'Retained name', default_theme = 'DARK',
                            show_research_highlights = true, featured_project_limit = 2,
                            latest_writing_limit = 20, version = 8
                        """);
            }
            configuration.target(MigrationVersion.fromVersion("22"));
            configuration.load().migrate();
            try (Connection connection = database.createConnection("");
                    var statement = connection.createStatement();
                    ResultSet row = statement.executeQuery("""
                            SELECT site_name, standard_public_theme, promote_research_highlights,
                                   featured_project_limit, latest_writing_limit,
                                   research_highlight_limit, version
                            FROM portfolio.site_presentation_settings
                            """)) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString("site_name")).isEqualTo("Retained name");
                assertThat(row.getString("standard_public_theme")).isEqualTo("DARK");
                assertThat(row.getBoolean("promote_research_highlights")).isTrue();
                assertThat(row.getInt("featured_project_limit")).isEqualTo(2);
                assertThat(row.getInt("latest_writing_limit")).isEqualTo(3);
                assertThat(row.getInt("research_highlight_limit")).isEqualTo(3);
                assertThat(row.getLong("version")).isEqualTo(8);
                assertThat(row.next()).isFalse();
            }
        } finally {
            database.close();
        }
    }
}
