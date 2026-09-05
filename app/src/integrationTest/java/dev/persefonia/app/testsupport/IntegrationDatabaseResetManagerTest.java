package dev.persefonia.app.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IntegrationDatabaseResetManagerTest {
    private static final SharedPostgresTestServer.Database POSTGRES = SharedPostgresTestServer.integrationDatabase();

    @Test
    void resetRemovesMutableRowsAndRestoresEveryMigrationEstablishedBaselineRow() throws Exception {
        UUID batchId = UUID.randomUUID();
        long migrationHistoryCount;
        try (Connection connection = POSTGRES.createConnection("")) {
            migrationHistoryCount = singleLong(connection, "SELECT count(*) FROM operations.flyway_schema_history");
            execute(connection, "UPDATE portfolio.site_presentation_settings SET site_name = 'Mutated'");
            execute(connection, "DELETE FROM portfolio.active_cv_profiles");
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO operations.cache_invalidation_batches
                        (id, reason, requested_by, requested_at, status, completed_at, failure_reason, version, running_since)
                    VALUES (?, 'PUBLIC_RESOURCE_CHANGED', 'SYSTEM', CURRENT_TIMESTAMP, 'REQUESTED', NULL, NULL, 0, NULL)
                    """)) {
                statement.setObject(1, batchId);
                statement.executeUpdate();
            }
        }

        IntegrationDatabaseManager.reset(POSTGRES);

        try (Connection connection = POSTGRES.createConnection("")) {
            assertThat(singleString(connection, "SELECT site_name FROM portfolio.site_presentation_settings"))
                    .isEqualTo("Persefonia");
            assertThat(singleString(connection, "SELECT default_language FROM portfolio.site_presentation_settings"))
                    .isEqualTo("TR");
            assertThat(singleLong(connection, "SELECT count(*) FROM portfolio.site_supported_languages"))
                    .isEqualTo(2);
            assertThat(singleLong(connection, "SELECT count(*) FROM portfolio.active_cv_profiles"))
                    .isEqualTo(1);
            assertThat(singleLong(connection, "SELECT count(*) FROM operations.cache_invalidation_batches"))
                    .isZero();
            assertThat(singleLong(connection, "SELECT count(*) FROM operations.flyway_schema_history"))
                    .isEqualTo(migrationHistoryCount);
        }
    }

    private static void execute(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private static String singleString(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getString(1);
        }
    }

    private static long singleLong(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
    }
}
