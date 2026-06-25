package dev.persefonia.app.communication.migration;

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

class CommunicationMigrationTest {
    private static final List<String> FORBIDDEN_CONTACT_COLUMNS = List.of(
            "raw_ip",
            "ip_address",
            "hashed_ip",
            "ip_hash",
            "user_agent",
            "user_agent_summary",
            "user_agent_hash",
            "rate_limit_key",
            "client_fingerprint",
            "session_id",
            "visitor_id",
            "tracking_cookie_id",
            "country_code");
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
    void clearCommunicationFoundationTables() {
        jdbc.execute("TRUNCATE communication.contact_messages CASCADE");
    }

    @Test
    void communicationFoundationTablesExist() {
        assertThat(communicationTables())
                .contains(
                        "contact_messages",
                        "mail_notification_attempts",
                        "contact_message_status_changes");
    }

    @Test
    void invalidContactStatusAndMailResultAreRejected() {
        assertThatThrownBy(() -> insertContactMessage(UUID.randomUUID(), "UNKNOWN", "NOT_ATTEMPTED"))
                .isInstanceOf(DataIntegrityViolationException.class);

        UUID contactMessageId = UUID.randomUUID();
        insertContactMessage(contactMessageId, "NEW", "NOT_ATTEMPTED");
        assertThatThrownBy(() -> insertMailAttempt(contactMessageId, "UNKNOWN"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void statusChangeRequiresDifferentStatuses() {
        UUID contactMessageId = UUID.randomUUID();
        insertContactMessage(contactMessageId, "NEW", "NOT_ATTEMPTED");

        assertThatThrownBy(() -> insertStatusChange(contactMessageId, "NEW", "NEW"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameContextForeignKeysAreEnforced() {
        UUID missingContactMessageId = UUID.randomUUID();

        assertThatThrownBy(() -> insertMailAttempt(missingContactMessageId, "FAILED"))
                .isInstanceOf(DataIntegrityViolationException.class);

        UUID contactMessageId = UUID.randomUUID();
        insertContactMessage(contactMessageId, "NEW", "NOT_ATTEMPTED");
        insertMailAttempt(contactMessageId, "FAILED");

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM communication.mail_notification_attempts
                WHERE contact_message_id = ?
                """, Integer.class, contactMessageId)).isEqualTo(1);
    }

    @Test
    void changedByAdminIdHasNoPhysicalIamForeignKey() {
        Integer foreignKeysOnChangedBy = jdbc.queryForObject("""
                SELECT count(*)
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON tc.constraint_schema = kcu.constraint_schema
                 AND tc.constraint_name = kcu.constraint_name
                WHERE tc.table_schema = 'communication'
                  AND tc.table_name = 'contact_message_status_changes'
                  AND tc.constraint_type = 'FOREIGN KEY'
                  AND kcu.column_name = 'changed_by_admin_id'
                """, Integer.class);

        assertThat(foreignKeysOnChangedBy).isZero();
    }

    @Test
    void contactMessagesHasNoForbiddenPrivacyColumnsAndNoSeedData() {
        List<String> columns = jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'communication' AND table_name = 'contact_messages'
                """, String.class);

        assertThat(columns).doesNotContainAnyElementsOf(FORBIDDEN_CONTACT_COLUMNS);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM communication.contact_messages", Integer.class))
                .isZero();
    }

    private static List<String> communicationTables() {
        return jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'communication'
                """, String.class);
    }

    private static void insertContactMessage(UUID id, String status, String mailDeliveryStatus) {
        Instant now = Instant.parse("2026-06-25T10:00:00Z");
        jdbc.update("""
                INSERT INTO communication.contact_messages (
                    id, sender_name, sender_email, subject, body, status, mail_delivery_status,
                    submitted_at, updated_at, version
                ) VALUES (?, 'Ada', 'ada@example.test', 'Hello', 'Body', ?, ?, ?, ?, 0)
                """, id, status, mailDeliveryStatus, Timestamp.from(now), Timestamp.from(now));
    }

    private static void insertMailAttempt(UUID contactMessageId, String result) {
        jdbc.update("""
                INSERT INTO communication.mail_notification_attempts (
                    id, contact_message_id, result, attempted_at, failure_reason
                ) VALUES (?, ?, ?, ?, 'safe failure')
                """, UUID.randomUUID(), contactMessageId, result, Timestamp.from(Instant.parse("2026-06-25T10:01:00Z")));
    }

    private static void insertStatusChange(UUID contactMessageId, String previousStatus, String newStatus) {
        jdbc.update("""
                INSERT INTO communication.contact_message_status_changes (
                    id, contact_message_id, previous_status, new_status, changed_by_admin_id, changed_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                contactMessageId,
                previousStatus,
                newStatus,
                UUID.randomUUID(),
                Timestamp.from(Instant.parse("2026-06-25T10:02:00Z")));
    }
}
