package dev.persefonia.app.testsupport;

import org.flywaydb.core.Flyway;

/** Owns the one normal integration schema bootstrap and central reset lifecycle. */
public final class IntegrationDatabaseManager {
    private static final IntegrationDatabaseResetManager RESET_MANAGER = new IntegrationDatabaseResetManager();
    private static boolean migrated;
    private static SharedPostgresTestServer.Database integrationDatabase;

    private IntegrationDatabaseManager() {}

    static synchronized void prepare(SharedPostgresTestServer.Database database, boolean created) {
        integrationDatabase = database;
        if (!migrated) {
            Flyway.configure()
                    .dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
                    .locations("classpath:db/migration")
                    .defaultSchema("operations")
                    .schemas("operations")
                    .createSchemas(true)
                    .load()
                    .migrate();
            migrated = true;
        }
        if (!created || migrated) reset(database);
    }

    public static synchronized void reset(SharedPostgresTestServer.Database database) {
        RESET_MANAGER.reset(database);
    }

    static synchronized void cleanBeforeTestMethod() {
        if (integrationDatabase != null && migrated) reset(integrationDatabase);
    }
}
