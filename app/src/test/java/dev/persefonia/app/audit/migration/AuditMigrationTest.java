package dev.persefonia.app.audit.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

class AuditMigrationTest {
    private static final List<String> FORBIDDEN_AUDIT_COLUMNS = List.of(
            "token",
            "session",
            "password",
            "raw_ip",
            "hashed_ip",
            "ip_address",
            "user_agent",
            "user_agent_summary",
            "fingerprint",
            "rate_limit_key",
            "contact_body",
            "body",
            "request_uri",
            "query_string",
            "headers",
            "smtp_secret",
            "cloudflare_secret",
            "private_config",
            "request_payload",
            "response_payload",
            "principal_payload",
            "claims",
            "cookie",
            "authorization",
            "updated_at",
            "version",
            "active",
            "status",
            "deleted_at",
            "archived_at");
    private static final Instant OCCURRED_AT = Instant.parse("2026-06-25T10:00:00Z");
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrate() {
        POSTGRES.start();
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .defaultSchema("operations")
                .schemas("operations")
                .createSchemas(true)
                .cleanDisabled(false)
                .load()
                .migrate();
    }

    @AfterAll
    static void stopDatabase() {
        POSTGRES.stop();
    }

    @BeforeEach
    void clearAuditTables() {
        jdbc.execute("TRUNCATE audit.audit_records CASCADE");
    }

    @Test
    void auditFoundationTablesExist() {
        assertThat(auditTables())
                .contains("audit_records", "audit_record_changes", "audit_record_metadata");
    }

    @Test
    void invalidAdminActorWithoutReferenceIsRejected() {
        assertThatThrownBy(() -> insertAdminRecord(UUID.randomUUID(), null, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void invalidSystemActorWithReferenceIsRejected() {
        assertThatThrownBy(() -> insertSystemRecord(UUID.randomUUID(), "iam"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void changeWithBothValuesNullIsRejected() {
        UUID recordId = UUID.randomUUID();
        insertSystemRecord(recordId, null);

        assertThatThrownBy(() -> insertChange(recordId, "title", null, null, 0))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateChangeFieldPathIsRejected() {
        UUID recordId = UUID.randomUUID();
        insertSystemRecord(recordId, null);
        insertChange(recordId, "title", null, "First", 0);

        assertThatThrownBy(() -> insertChange(recordId, "title", null, "Second", 1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateChangePositionIsRejected() {
        UUID recordId = UUID.randomUUID();
        insertSystemRecord(recordId, null);
        insertChange(recordId, "title", null, "First", 0);

        assertThatThrownBy(() -> insertChange(recordId, "summary", null, "Second", 0))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateMetadataKeyIsRejected() {
        UUID recordId = UUID.randomUUID();
        insertSystemRecord(recordId, null);
        insertMetadata(recordId, "reason", "first", 0);

        assertThatThrownBy(() -> insertMetadata(recordId, "reason", "second", 1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateMetadataPositionIsRejected() {
        UUID recordId = UUID.randomUUID();
        insertSystemRecord(recordId, null);
        insertMetadata(recordId, "reason", "first", 0);

        assertThatThrownBy(() -> insertMetadata(recordId, "channel", "second", 0))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void forbiddenColumnsAreAbsent() {
        assertThat(columns("audit_records")).doesNotContainAnyElementsOf(FORBIDDEN_AUDIT_COLUMNS);
        assertThat(columns("audit_record_changes")).doesNotContainAnyElementsOf(FORBIDDEN_AUDIT_COLUMNS);
        assertThat(columns("audit_record_metadata")).doesNotContainAnyElementsOf(FORBIDDEN_AUDIT_COLUMNS);
    }

    @Test
    void noCrossContextPhysicalForeignKeysExist() {
        Integer crossContextForeignKeys = jdbc.queryForObject("""
                SELECT count(*)
                FROM information_schema.referential_constraints rc
                JOIN information_schema.table_constraints child
                  ON rc.constraint_schema = child.constraint_schema
                 AND rc.constraint_name = child.constraint_name
                JOIN information_schema.table_constraints parent
                  ON rc.unique_constraint_schema = parent.constraint_schema
                 AND rc.unique_constraint_name = parent.constraint_name
                WHERE child.table_schema = 'audit'
                  AND parent.table_schema <> 'audit'
                """, Integer.class);

        assertThat(crossContextForeignKeys).isZero();
    }

    @Test
    void auditChildForeignKeysDoNotCascadeRootDeletes() {
        List<String> deleteRules = jdbc.queryForList("""
                SELECT delete_rule
                FROM information_schema.referential_constraints
                WHERE constraint_schema = 'audit'
                  AND constraint_name IN (
                      'audit_record_changes_record_fk',
                      'audit_record_metadata_record_fk')
                ORDER BY constraint_name
                """, String.class);

        assertThat(deleteRules).containsExactly("NO ACTION", "NO ACTION");
    }

    @Test
    void childRowsPreventRootDelete() {
        UUID recordId = UUID.randomUUID();
        insertSystemRecord(recordId, null);
        insertChange(recordId, "title", null, "First", 0);
        insertMetadata(recordId, "reason", "scheduled", 0);

        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit.audit_records WHERE id = ?", recordId))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit.audit_records WHERE id = ?", Integer.class, recordId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit.audit_record_changes WHERE audit_record_id = ?", Integer.class, recordId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit.audit_record_metadata WHERE audit_record_id = ?", Integer.class, recordId))
                .isEqualTo(1);
    }

    private static List<String> auditTables() {
        return jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'audit'
                """, String.class);
    }

    private static List<String> columns(String tableName) {
        return jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'audit' AND table_name = ?
                """, String.class, tableName);
    }

    private static void insertAdminRecord(UUID id, String context, String sourceType, UUID actorId) {
        jdbc.update("""
                INSERT INTO audit.audit_records (
                    id, action, actor_type, actor_context, actor_source_type, actor_id, actor_display,
                    entity_context, entity_type, entity_id, request_id, occurred_at, created_at
                ) VALUES (?, 'content.published', 'ADMIN', ?, ?, ?, 'Jane Admin',
                    'publishing', 'content_item', ?, NULL, ?, ?)
                """,
                id, context, sourceType, actorId, UUID.randomUUID(),
                Timestamp.from(OCCURRED_AT), Timestamp.from(OCCURRED_AT));
    }

    private static void insertSystemRecord(UUID id, String context) {
        jdbc.update("""
                INSERT INTO audit.audit_records (
                    id, action, actor_type, actor_context, actor_source_type, actor_id, actor_display,
                    entity_context, entity_type, entity_id, request_id, occurred_at, created_at
                ) VALUES (?, 'content.unpublished', 'SYSTEM', ?, NULL, NULL, 'System',
                    'publishing', 'content_item', ?, NULL, ?, ?)
                """,
                id, context, UUID.randomUUID(),
                Timestamp.from(OCCURRED_AT), Timestamp.from(OCCURRED_AT));
    }

    private static void insertChange(UUID recordId, String fieldPath, String oldValue, String newValue, int position) {
        jdbc.update("""
                INSERT INTO audit.audit_record_changes (
                    id, audit_record_id, field_path, old_value, new_value, position
                ) VALUES (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), recordId, fieldPath, oldValue, newValue, position);
    }

    private static void insertMetadata(UUID recordId, String key, String value, int position) {
        jdbc.update("""
                INSERT INTO audit.audit_record_metadata (
                    id, audit_record_id, metadata_key, metadata_value, position
                ) VALUES (?, ?, ?, ?, ?)
                """, UUID.randomUUID(), recordId, key, value, position);
    }
}
