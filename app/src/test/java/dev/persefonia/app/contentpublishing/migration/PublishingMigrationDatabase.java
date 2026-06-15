package dev.persefonia.app.contentpublishing.migration;

import java.sql.SQLException;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.testcontainers.postgresql.PostgreSQLContainer;

final class PublishingMigrationDatabase {
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private PublishingMigrationDatabase() {
    }

    static void start() {
        POSTGRES.start();
    }

    static void stop() {
        POSTGRES.stop();
    }

    static MigrateResult cleanMigrate() {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .defaultSchema("operations")
                .schemas("operations")
                .createSchemas(true)
                .cleanDisabled(false)
                .load();

        flyway.clean();
        return flyway.migrate();
    }

    static void truncatePublishing() throws SQLException {
        PublishingSql.execute("""
                TRUNCATE publishing.translation_group_entries,
                    publishing.translation_groups,
                    publishing.content_revisions,
                    publishing.content_rendered_headings,
                    publishing.content_render_snapshots,
                    publishing.content_items
                RESTART IDENTITY CASCADE
                """);
    }
}
