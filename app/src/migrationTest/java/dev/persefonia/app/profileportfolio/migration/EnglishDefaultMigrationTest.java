package dev.persefonia.app.profileportfolio.migration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.app.testsupport.SharedPostgresTestServer;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class EnglishDefaultMigrationTest {
    @ParameterizedTest
    @ValueSource(strings = {"TR", "EN"})
    void upgradesExistingSettingsToEnglishWithoutChangingOtherChoices(String originalLanguage) {
        try (var database = SharedPostgresTestServer.migrationDatabase()) {
            database.start();
            var dataSource = new DriverManagerDataSource(
                    database.getJdbcUrl(), database.getUsername(), database.getPassword());
            var jdbc = new JdbcTemplate(dataSource);
            var configuration = Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .defaultSchema("operations")
                    .schemas("operations")
                    .createSchemas(true);
            configuration.target("23").load().migrate();
            Instant originalTime = Instant.parse("2020-01-01T00:00:00Z");
            jdbc.update("""
                    UPDATE portfolio.site_presentation_settings
                    SET default_language = ?, site_name = 'Retained name',
                        default_meta_description = 'Retained description', standard_public_theme = 'DARK',
                        promote_research_highlights = true, featured_project_limit = 2,
                        updated_at = ?, version = 8
                    """, originalLanguage, Timestamp.from(originalTime));
            if (originalLanguage.equals("TR")) {
                jdbc.update("DELETE FROM portfolio.site_supported_languages WHERE language = 'EN'");
            }
            var before = jdbc.queryForMap("SELECT * FROM portfolio.site_presentation_settings");

            configuration.target("24").load().migrate();

            var after = jdbc.queryForMap("SELECT * FROM portfolio.site_presentation_settings");
            assertThat(after).containsEntry("default_language", "EN");
            assertThat(after.get("version")).isEqualTo(originalLanguage.equals("EN") ? 8L : 9L);
            assertThat((Timestamp) after.get("updated_at"))
                    .isAfterOrEqualTo(Timestamp.from(originalTime));
            if (originalLanguage.equals("EN")) {
                assertThat(after.get("updated_at")).isEqualTo(before.get("updated_at"));
            }
            for (String field : List.of("default_language", "version", "updated_at")) {
                before.remove(field);
                after.remove(field);
            }
            assertThat(after).isEqualTo(before);
            assertThat(jdbc.queryForList("SELECT language FROM portfolio.site_supported_languages", String.class))
                    .containsExactlyInAnyOrder("EN", "TR");
            assertThat(configuration.load().migrate().migrationsExecuted).isZero();
        }
    }
}
