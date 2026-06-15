package dev.persefonia.app.infrastructure;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

class FlywayMigrationTest {
    private static final List<String> LOCKED_SCHEMAS = List.of(
            "iam",
            "taxonomy",
            "publishing",
            "portfolio",
            "media",
            "communication",
            "discovery",
            "integrity",
            "insights",
            "audit",
            "portability",
            "operations");

    @Test
    void migratesLockedSchemasAndCreatesHistoryTable() throws SQLException {
        try (PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine")) {
            postgres.start();

            Flyway flyway = Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration")
                    .defaultSchema("operations")
                    .schemas("operations")
                    .createSchemas(true)
                    .cleanDisabled(false)
                    .load();

            assertDoesNotThrow(flyway::clean);
            var migrationResult = assertDoesNotThrow(flyway::migrate);
            assertTrue(
                    migrationResult.migrationsExecuted >= 5,
                    "Required baseline migrations should be executable without forbidding future migrations");

            try (Connection connection = postgres.createConnection("")) {
                for (String schema : LOCKED_SCHEMAS) {
                    assertSchemaExists(connection, schema);
                }
                assertFlywayHistoryTableExists(connection);
                assertTableExists(connection, "taxonomy.tags");
            }
        }
    }

    private static void assertSchemaExists(Connection connection, String schema) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT schema_name
                FROM information_schema.schemata
                WHERE schema_name = ?
                """)) {
            statement.setString(1, schema);
            try (ResultSet result = statement.executeQuery()) {
                assertNotNull(result.next() ? result.getString("schema_name") : null, "Missing schema: " + schema);
            }
        }
    }

    private static void assertFlywayHistoryTableExists(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT to_regclass('operations.flyway_schema_history')")) {
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                assertNotNull(result.getString(1), "Missing operations.flyway_schema_history");
            }
        }
    }

    private static void assertTableExists(Connection connection, String qualifiedName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT to_regclass(?)")) {
            statement.setString(1, qualifiedName);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                assertNotNull(result.getString(1), "Missing table: " + qualifiedName);
            }
        }
    }
}
