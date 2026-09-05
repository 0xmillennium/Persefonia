package dev.persefonia.app.testsupport;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Sole PostgreSQL process owner for each DB-backed app test JVM. */
public final class SharedPostgresTestServer {
    private static final String USERNAME = "persefonia";
    private static final String PASSWORD = "persefonia_dev";
    private static final AtomicInteger DATABASE_SEQUENCE = new AtomicInteger();
    private static final PostgreSQLContainer SERVER = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("postgres")
            .withUsername(USERNAME)
            .withPassword(PASSWORD);

    private SharedPostgresTestServer() {}

    public static Database integrationDatabase() {
        return new Database("persefonia_integration", false);
    }

    public static Database migrationDatabase() {
        return new Database("persefonia_migration_" + DATABASE_SEQUENCE.incrementAndGet(), true);
    }

    private static synchronized void startServer() {
        if (!SERVER.isRunning()) SERVER.start();
    }

    private static String jdbcUrl(String database) {
        startServer();
        return "jdbc:postgresql://" + SERVER.getHost() + ":" + SERVER.getMappedPort(5432) + "/" + database;
    }

    private static synchronized boolean createDatabase(String database) {
        try (Connection connection = DriverManager.getConnection(jdbcUrl("postgres"), USERNAME, PASSWORD);
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + quote(database));
            return true;
        } catch (SQLException exception) {
            if ("42P04".equals(exception.getSQLState())) return false;
            throw new IllegalStateException("Could not create test database " + database, exception);
        }
    }

    private static synchronized void dropDatabase(String database) {
        try (Connection connection = DriverManager.getConnection(jdbcUrl("postgres"), USERNAME, PASSWORD);
                Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + quote(database));
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not drop test database " + database, exception);
        }
    }

    static String quote(String identifier) {
        if (!identifier.matches("[a-z][a-z0-9_]{0,55}")) throw new IllegalArgumentException("Unsafe identifier: " + identifier);
        return '"' + identifier + '"';
    }

    public static final class Database implements AutoCloseable {
        private String name;
        private final boolean isolated;
        private boolean started;

        private Database(String name, boolean isolated) { this.name = name; this.isolated = isolated; }

        public Database withDatabaseName(String name) { if (isolated && !started) this.name = name; return this; }
        public Database withUsername(String ignored) { return this; }
        public Database withPassword(String ignored) { return this; }

        public synchronized void start() {
            if (started) return;
            boolean created = createDatabase(name);
            started = true;
            if (!isolated) IntegrationDatabaseManager.prepare(this, created);
        }

        public synchronized void stop() { if (isolated && started) dropDatabase(name); started = false; }
        public synchronized boolean isRunning() { return started; }
        public String getJdbcUrl() { start(); return jdbcUrl(name); }
        public String getUsername() { return USERNAME; }
        public String getPassword() { return PASSWORD; }
        public Connection createConnection(String ignored) throws SQLException { return DriverManager.getConnection(getJdbcUrl(), USERNAME, PASSWORD); }
        @Override public void close() { stop(); }
    }
}
