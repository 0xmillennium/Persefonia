package dev.persefonia.app.testsupport;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Performs a bounded, transactional integration-database reset and seed restore. */
final class IntegrationDatabaseResetManager {
    private static final List<String> APPLICATION_SCHEMAS = List.of(
            "iam", "taxonomy", "publishing", "portfolio", "media", "communication", "discovery",
            "integrity", "insights", "audit", "portability", "operations");

    private List<String> applicationTables;

    synchronized void reset(SharedPostgresTestServer.Database database) {
        try (Connection connection = database.createConnection("")) {
            connection.setAutoCommit(false);
            try {
                if (applicationTables == null) applicationTables = discoverApplicationTables(connection);
                try (Statement statement = connection.createStatement()) {
                    statement.execute("TRUNCATE TABLE " + String.join(", ", applicationTables)
                            + " RESTART IDENTITY CASCADE");
                }
                IntegrationBaselineRestorer.restore(connection);
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Could not reset the integration database", exception);
        }
    }

    private static List<String> discoverApplicationTables(Connection connection) throws SQLException {
        String placeholders = String.join(", ", APPLICATION_SCHEMAS.stream().map(ignored -> "?").toList());
        String query = """
                SELECT quote_ident(schemaname) || '.' || quote_ident(tablename)
                FROM pg_catalog.pg_tables
                WHERE schemaname IN (%s)
                  AND tablename <> 'flyway_schema_history'
                ORDER BY schemaname, tablename
                """.formatted(placeholders);
        List<String> tables = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            for (int index = 0; index < APPLICATION_SCHEMAS.size(); index++) {
                statement.setString(index + 1, APPLICATION_SCHEMAS.get(index));
            }
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) tables.add(rows.getString(1));
            }
        }
        if (tables.isEmpty()) throw new IllegalStateException("No Persefonia application tables found for reset");
        return List.copyOf(tables);
    }
}
