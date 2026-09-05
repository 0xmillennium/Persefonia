package dev.persefonia.app.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import dev.persefonia.app.testsupport.SharedPostgresTestServer;

class AdminAccountMigrationTest {
    private static final SharedPostgresTestServer.Database POSTGRES = SharedPostgresTestServer.migrationDatabase();
    private static long initialAdminAccountCount;
    private static long initialAdminAccountRoleCount;

    @BeforeAll
    static void migrateDatabase() throws SQLException {
        POSTGRES.start();
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .defaultSchema("operations")
                .schemas("operations")
                .createSchemas(true)
                .cleanDisabled(false)
                .load();

        flyway.clean();
        flyway.migrate();
        initialAdminAccountCount = queryLong("SELECT count(*) FROM iam.admin_accounts");
        initialAdminAccountRoleCount = queryLong("SELECT count(*) FROM iam.admin_account_roles");
    }

    @AfterAll
    static void stopDatabase() {
        POSTGRES.stop();
    }

    @BeforeEach
    void clearAdminAccounts() throws SQLException {
        execute("TRUNCATE iam.admin_accounts CASCADE");
    }

    @Test
    void tablesExist() throws SQLException {
        assertThat(queryStrings("""
                SELECT to_regclass('iam.admin_accounts')::text
                UNION ALL
                SELECT to_regclass('iam.admin_account_roles')::text
                """))
                .containsExactlyInAnyOrder("iam.admin_accounts", "iam.admin_account_roles");
    }

    @Test
    void adminAccountsColumnsMatchExpected() throws SQLException {
        assertThat(columnsFor("admin_accounts")).containsExactlyInAnyOrder(
                "id",
                "oidc_subject",
                "email",
                "normalized_email",
                "display_name",
                "status",
                "last_login_at",
                "created_at",
                "updated_at",
                "version");
    }

    @Test
    void adminAccountRolesColumnsMatchExpected() throws SQLException {
        assertThat(columnsFor("admin_account_roles"))
                .containsExactlyInAnyOrder("admin_account_id", "role");
    }

    @Test
    void constraintsExist() throws SQLException {
        assertThat(queryStrings("""
                SELECT constraint_name
                FROM information_schema.table_constraints
                WHERE table_schema = 'iam'
                  AND table_name IN ('admin_accounts', 'admin_account_roles')
                """))
                .contains(
                        "pk_admin_accounts",
                        "uk_admin_accounts_oidc_subject",
                        "uk_admin_accounts_normalized_email",
                        "chk_admin_accounts_oidc_subject_not_blank",
                        "chk_admin_accounts_email_not_blank",
                        "chk_admin_accounts_normalized_email_not_blank",
                        "chk_admin_accounts_display_name_not_blank",
                        "chk_admin_accounts_oidc_subject_max_length",
                        "chk_admin_accounts_email_max_length",
                        "chk_admin_accounts_normalized_email_max_length",
                        "chk_admin_accounts_display_name_max_length",
                        "chk_admin_accounts_normalized_email_lowercase",
                        "chk_admin_accounts_oidc_subject_trimmed",
                        "chk_admin_accounts_email_trimmed",
                        "chk_admin_accounts_normalized_email_trimmed",
                        "chk_admin_accounts_display_name_trimmed",
                        "chk_admin_accounts_status",
                        "chk_admin_accounts_updated_at_not_before_created_at",
                        "chk_admin_accounts_last_login_at_not_before_created_at",
                        "chk_admin_accounts_version_non_negative",
                        "pk_admin_account_roles",
                        "fk_admin_account_roles_admin_account",
                        "chk_admin_account_roles_role");
    }

    @Test
    void statusIndexExists() throws SQLException {
        assertThat(queryStrings("""
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = 'iam' AND tablename = 'admin_accounts'
                """))
                .contains("idx_admin_accounts_status");
    }

    @Test
    void rolesAreStoredInSeparateTable() throws SQLException {
        assertThat(columnsFor("admin_accounts")).doesNotContain("roles", "role");
        assertThat(columnsFor("admin_account_roles")).contains("role");
        assertThat(queryLong("""
                SELECT count(*)
                FROM information_schema.columns
                WHERE table_schema = 'iam'
                  AND table_name IN ('admin_accounts', 'admin_account_roles')
                  AND (data_type = 'ARRAY' OR data_type IN ('json', 'jsonb'))
                """))
                .isZero();
    }

    @Test
    void forbiddenStorageColumnsAreAbsent() throws SQLException {
        List<String> forbidden = List.of(
                "password",
                "password_hash",
                "token",
                "access_token",
                "refresh_token",
                "id_token",
                "oidc_token",
                "session",
                "security_context");

        assertThat(queryStrings("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'iam'
                  AND table_name IN ('admin_accounts', 'admin_account_roles')
                """))
                .noneMatch(column -> forbidden.stream().anyMatch(column::contains));
    }

    @Test
    void constraintsRejectInvalidData() throws SQLException {
        String accountId = UUID.randomUUID().toString();
        insertAccount(accountId, "subject-1", "owner-1@example.com", "ACTIVE");

        assertAccountRejected(UUID.randomUUID(), " ", "owner@example.com", "owner-2@example.com", "Owner", "ACTIVE");
        assertAccountRejected(UUID.randomUUID(), "subject-2", " ", "owner-2@example.com", "Owner", "ACTIVE");
        assertAccountRejected(UUID.randomUUID(), "subject-3", "owner@example.com", " ", "Owner", "ACTIVE");
        assertAccountRejected(UUID.randomUUID(), "subject-4", "owner@example.com", "owner-4@example.com", " ", "ACTIVE");
        assertAccountRejected(UUID.randomUUID(), "subject-5", "owner@example.com", "owner-5@example.com", "Owner", "PENDING");
        assertSqlRejected("INSERT INTO iam.admin_account_roles (admin_account_id, role) VALUES ('"
                + accountId + "', 'ADMIN')");
        assertAccountRejected(UUID.randomUUID(), "subject-1", "owner@example.com", "owner-6@example.com", "Owner", "ACTIVE");
        assertAccountRejected(UUID.randomUUID(), "subject-7", "owner@example.com", "owner-1@example.com", "Owner", "ACTIVE");
    }

    @Test
    void rejectsTooLongOidcSubject() {
        assertAccountRejected(
                UUID.randomUUID(),
                "s".repeat(513),
                "owner@example.com",
                "owner@example.com",
                "Owner",
                "ACTIVE");
    }

    @Test
    void rejectsTooLongEmail() {
        assertAccountRejected(
                UUID.randomUUID(),
                "subject-too-long-email",
                "a".repeat(309) + "@example.com",
                "owner@example.com",
                "Owner",
                "ACTIVE");
    }

    @Test
    void rejectsTooLongNormalizedEmail() {
        assertAccountRejected(
                UUID.randomUUID(),
                "subject-too-long-normalized-email",
                "owner@example.com",
                "a".repeat(309) + "@example.com",
                "Owner",
                "ACTIVE");
    }

    @Test
    void rejectsTooLongDisplayName() {
        assertAccountRejected(
                UUID.randomUUID(),
                "subject-too-long-display-name",
                "owner@example.com",
                "owner@example.com",
                "O".repeat(201),
                "ACTIVE");
    }

    @Test
    void rejectsUppercaseNormalizedEmail() {
        assertAccountRejected(
                UUID.randomUUID(),
                "subject-uppercase-normalized-email",
                "owner@example.com",
                "Owner@Example.com",
                "Owner",
                "ACTIVE");
    }

    @Test
    void rejectsUpdatedAtBeforeCreatedAt() {
        assertTimedAccountRejected(
                UUID.randomUUID(),
                "subject-updated-before-created",
                "owner@example.com",
                "owner@example.com",
                "Owner",
                "ACTIVE",
                "'2026-06-11T08:00:00Z'::timestamp with time zone",
                "'2026-06-11T07:59:59Z'::timestamp with time zone",
                null);
    }

    @Test
    void rejectsLastLoginAtBeforeCreatedAt() {
        assertTimedAccountRejected(
                UUID.randomUUID(),
                "subject-login-before-created",
                "owner@example.com",
                "owner@example.com",
                "Owner",
                "ACTIVE",
                "'2026-06-11T08:00:00Z'::timestamp with time zone",
                "'2026-06-11T08:00:00Z'::timestamp with time zone",
                "2026-06-11T07:59:59Z");
    }

    @Test
    void rejectsUntrimmedEmail() {
        assertAccountRejected(
                UUID.randomUUID(),
                "subject-untrimmed-email",
                " owner@example.com",
                "owner@example.com",
                "Owner",
                "ACTIVE");
    }

    @Test
    void rejectsUntrimmedNormalizedEmail() {
        assertAccountRejected(
                UUID.randomUUID(),
                "subject-untrimmed-normalized-email",
                "owner@example.com",
                "owner@example.com ",
                "Owner",
                "ACTIVE");
    }

    @Test
    void rejectsUntrimmedDisplayName() {
        assertAccountRejected(
                UUID.randomUUID(),
                "subject-untrimmed-display-name",
                "owner@example.com",
                "owner@example.com",
                " Owner",
                "ACTIVE");
    }

    @Test
    void roleRowsCascadeWhenAccountDeleted() throws SQLException {
        String accountId = UUID.randomUUID().toString();
        insertAccount(accountId, "cascade-subject", "cascade@example.com", "ACTIVE");
        execute("INSERT INTO iam.admin_account_roles (admin_account_id, role) VALUES ('"
                + accountId + "', 'OWNER')");

        execute("DELETE FROM iam.admin_accounts WHERE id = '" + accountId + "'");

        assertThat(queryLong("SELECT count(*) FROM iam.admin_account_roles")).isZero();
    }

    @Test
    void noSeedData() throws SQLException {
        assertThat(initialAdminAccountCount).isZero();
        assertThat(initialAdminAccountRoleCount).isZero();
        assertThat(queryLong("SELECT count(*) FROM iam.admin_accounts")).isZero();
        assertThat(queryLong("SELECT count(*) FROM iam.admin_account_roles")).isZero();
    }

    private static List<String> columnsFor(String tableName) throws SQLException {
        return queryStrings("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'iam' AND table_name = '%s'
                """.formatted(tableName));
    }

    private static void assertAccountRejected(
            UUID id,
            String oidcSubject,
            String email,
            String normalizedEmail,
            String displayName,
            String status) {
        assertThatThrownBy(() -> insertAccount(
                id.toString(),
                oidcSubject,
                email,
                normalizedEmail,
                displayName,
                status))
                .isInstanceOf(SQLException.class);
    }

    private static void assertTimedAccountRejected(
            UUID id,
            String oidcSubject,
            String email,
            String normalizedEmail,
            String displayName,
            String status,
            String createdAt,
            String updatedAt,
            String lastLoginAt) {
        assertThatThrownBy(() -> insertAccount(
                id.toString(),
                oidcSubject,
                email,
                normalizedEmail,
                displayName,
                status,
                createdAt,
                updatedAt,
                lastLoginAt))
                .isInstanceOf(SQLException.class);
    }

    private static void assertSqlRejected(String sql) {
        assertThatThrownBy(() -> execute(sql)).isInstanceOf(SQLException.class);
    }

    private static void insertAccount(
            String id,
            String oidcSubject,
            String normalizedEmail,
            String status) throws SQLException {
        insertAccount(id, oidcSubject, normalizedEmail, "Owner", status);
    }

    private static void insertAccount(
            String id,
            String oidcSubject,
            String normalizedEmail,
            String displayName,
            String status) throws SQLException {
        insertAccount(id, oidcSubject, "owner@example.com", normalizedEmail, displayName, status);
    }

    private static void insertAccount(
            String id,
            String oidcSubject,
            String email,
            String normalizedEmail,
            String displayName,
            String status) throws SQLException {
        insertAccount(
                id,
                oidcSubject,
                email,
                normalizedEmail,
                displayName,
                status,
                "now()",
                "now()",
                null);
    }

    private static void insertAccount(
            String id,
            String oidcSubject,
            String email,
            String normalizedEmail,
            String displayName,
            String status,
            String createdAt,
            String updatedAt,
            String lastLoginAt) throws SQLException {
        String lastLoginValue = lastLoginAt == null ? "NULL" : "?::timestamp with time zone";
        try (Connection connection = POSTGRES.createConnection("");
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO iam.admin_accounts (
                            id, oidc_subject, email, normalized_email, display_name,
                            status, created_at, updated_at, last_login_at, version
                        ) VALUES (?::uuid, ?, ?, ?, ?, ?, %s, %s, %s, 0)
                        """.formatted(createdAt, updatedAt, lastLoginValue))) {
            statement.setString(1, id);
            statement.setString(2, oidcSubject);
            statement.setString(3, email);
            statement.setString(4, normalizedEmail);
            statement.setString(5, displayName);
            statement.setString(6, status);
            if (lastLoginAt != null) {
                statement.setString(7, lastLoginAt);
            }
            statement.executeUpdate();
        }
    }

    private static void execute(String sql) throws SQLException {
        try (Connection connection = POSTGRES.createConnection("");
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        }
    }

    private static List<String> queryStrings(String sql) throws SQLException {
        try (Connection connection = POSTGRES.createConnection("");
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet result = statement.executeQuery()) {
            var values = new java.util.ArrayList<String>();
            while (result.next()) {
                values.add(result.getString(1));
            }
            return values;
        }
    }

    private static long queryLong(String sql) throws SQLException {
        try (Connection connection = POSTGRES.createConnection("");
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getLong(1);
        }
    }
}
