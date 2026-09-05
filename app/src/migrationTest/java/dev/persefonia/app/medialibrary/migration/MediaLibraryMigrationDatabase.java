package dev.persefonia.app.medialibrary.migration;

import org.flywaydb.core.Flyway;
import dev.persefonia.app.testsupport.SharedPostgresTestServer;

final class MediaLibraryMigrationDatabase {
    static final SharedPostgresTestServer.Database POSTGRES = SharedPostgresTestServer.migrationDatabase();

    private MediaLibraryMigrationDatabase() {
    }

    static void start() {
        if (!POSTGRES.isRunning()) {
            POSTGRES.start();
        }
    }

    static void cleanMigrate() {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .defaultSchema("operations")
                .schemas(
                        "operations", "iam", "taxonomy", "publishing", "portfolio", "media",
                        "communication", "discovery", "integrity", "insights", "audit",
                        "portability")
                .createSchemas(true)
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();
    }
}
