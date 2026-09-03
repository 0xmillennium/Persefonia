package dev.persefonia.app.audit.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.app.audit.persistence.AuditPersistenceException;
import dev.persefonia.audit.application.query.AuditRecordDetail;
import dev.persefonia.audit.application.query.AuditRecordListPage;
import dev.persefonia.audit.application.query.AuditSearchRequest;
import dev.persefonia.audit.domain.record.AuditAction;
import dev.persefonia.audit.domain.record.AuditActorType;
import dev.persefonia.audit.domain.record.AuditRecordId;
import dev.persefonia.audit.domain.record.SourceContext;
import dev.persefonia.audit.domain.record.SourceEntityId;
import dev.persefonia.audit.domain.record.SourceType;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

class JdbcAuditRecordReadAdapterTest {
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    private static final Instant CREATED = Instant.parse("2026-09-03T17:30:00Z");
    private static JdbcTemplate jdbc;
    private static JdbcAuditRecordReadAdapter records;

    @BeforeAll
    static void migrate() {
        POSTGRES.start();
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .defaultSchema("operations").schemas("operations").createSchemas(true).load().migrate();
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("namedParameterJdbcTemplate", new NamedParameterJdbcTemplate(dataSource));
        records = new JdbcAuditRecordReadAdapter(beans.getBeanProvider(NamedParameterJdbcTemplate.class));
    }

    @AfterAll
    static void stop() { POSTGRES.stop(); }

    @BeforeEach
    void clear() { jdbc.execute("TRUNCATE audit.audit_records CASCADE"); }

    @Test
    void defaultOrderingUsesOccurredAtThenIdAndListIgnoresChildren() {
        Instant same = Instant.parse("2026-09-03T17:00:00Z");
        UUID low = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID high = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        UUID newest = UUID.fromString("11111111-1111-1111-1111-111111111111");
        insertSystem(low, "content.published", "publishing", "content_item", UUID.randomUUID(), same);
        insertSystem(high, "content.published", "publishing", "content_item", UUID.randomUUID(), same);
        insertSystem(newest, "content.published", "publishing", "content_item", UUID.randomUUID(), same.plusSeconds(1));
        insertChange(newest, "status", "DRAFT", "PUBLISHED", 0);
        insertMetadata(newest, "reason", "manual review", 0);

        AuditRecordListPage page = records.search(AuditSearchRequest.firstPage());

        assertThat(page.items()).extracting(item -> item.id()).containsExactly(newest, high, low);
        assertThat(page.totalItems()).isEqualTo(3);
    }

    @Test
    void exactActionActorAndEntityFiltersWork() {
        UUID adminId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        UUID matching = insertAdmin("content.published", adminId, "publishing", "content_item", entityId,
                Instant.parse("2026-09-03T17:00:00Z"));
        insertAdmin("content.unpublished", adminId, "publishing", "content_item", entityId,
                Instant.parse("2026-09-03T17:01:00Z"));
        insertSystem(UUID.randomUUID(), "content.published", "publishing", "content_item", entityId,
                Instant.parse("2026-09-03T17:02:00Z"));

        assertIds(search(AuditAction.of("content.published"), AuditActorType.ADMIN, adminId,
                "publishing", "content_item", entityId, null, null, 1, 25), matching);
        assertThat(search(null, AuditActorType.SYSTEM, null, null, null, null, null, null, 1, 25).items())
                .singleElement().satisfies(item -> assertThat(item.actorType()).isEqualTo("SYSTEM"));
        assertThat(search(null, null, null, "publishing", null, null, null, null, 1, 25).totalItems())
                .isEqualTo(3);
        assertThat(search(null, null, null, "publishing", "content_item", null, null, null, 1, 25).totalItems())
                .isEqualTo(3);
    }

    @Test
    void occurredRangeIsFromInclusiveAndToExclusiveAndConditionsComposeWithAnd() {
        Instant from = Instant.parse("2026-09-03T17:00:00Z");
        UUID atFrom = insertAdmin("content.published", UUID.randomUUID(), "publishing", "content_item",
                UUID.randomUUID(), from);
        insertAdmin("content.published", UUID.randomUUID(), "publishing", "content_item",
                UUID.randomUUID(), from.plusSeconds(60));
        insertSystem(UUID.randomUUID(), "content.published", "publishing", "content_item",
                UUID.randomUUID(), from);

        AuditRecordListPage page = search(AuditAction.of("content.published"), AuditActorType.ADMIN, null,
                null, null, null, from, from.plusSeconds(60), 1, 25);

        assertIds(page, atFrom);
    }

    @Test
    void paginationReturnsItemsAndStableTotalAcrossPages() {
        for (int index = 0; index < 3; index++) {
            insertSystem(UUID.randomUUID(), "content.published", "publishing", "content_item",
                    UUID.randomUUID(), Instant.parse("2026-09-03T17:00:00Z").plusSeconds(index));
        }
        AuditRecordListPage first = search(null, null, null, null, null, null, null, null, 1, 2);
        AuditRecordListPage second = search(null, null, null, null, null, null, null, null, 2, 2);
        assertThat(first.items()).hasSize(2);
        assertThat(second.items()).hasSize(1);
        assertThat(first.totalItems()).isEqualTo(3);
        assertThat(second.totalItems()).isEqualTo(3);
        assertThat(second.page()).isEqualTo(2);
        assertThat(second.pageSize()).isEqualTo(2);
    }

    @Test
    void detailValidatesRootAndReturnsOrderedChildrenForAdminAndSystem() {
        UUID actorId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        UUID admin = insertAdmin("content.published", actorId, "publishing", "content_item", entityId,
                Instant.parse("2026-09-03T17:00:00Z"));
        jdbc.update("UPDATE audit.audit_records SET request_id = ? WHERE id = ?", "request-123", admin);
        insertChange(admin, "title", null, "New title", 1);
        insertChange(admin, "status", "DRAFT", "PUBLISHED", 0);
        insertMetadata(admin, "reason", "manual review", 1);
        insertMetadata(admin, "source.channel", "admin", 0);
        UUID system = UUID.randomUUID();
        insertSystem(system, "content.unpublished", "publishing", "content_item", UUID.randomUUID(),
                Instant.parse("2026-09-03T18:00:00Z"));

        AuditRecordDetail detail = records.findById(AuditRecordId.from(admin)).orElseThrow();

        assertThat(detail.id()).isEqualTo(admin);
        assertThat(detail.actorId()).isEqualTo(actorId);
        assertThat(detail.entityId()).isEqualTo(entityId);
        assertThat(detail.requestId()).isEqualTo("request-123");
        assertThat(detail.createdAt()).isEqualTo(CREATED);
        assertThat(detail.changes()).extracting(change -> change.fieldPath()).containsExactly("status", "title");
        assertThat(detail.metadata()).extracting(entry -> entry.key()).containsExactly("source.channel", "reason");
        assertThat(records.findById(AuditRecordId.from(system))).get().extracting(AuditRecordDetail::actorType)
                .isEqualTo("SYSTEM");
        assertThat(records.findById(AuditRecordId.newId())).isEmpty();
    }

    @Test
    void corruptedUnsafeChildFailsClosedWithoutEchoingValue() {
        UUID id = UUID.randomUUID();
        insertSystem(id, "content.published", "publishing", "content_item", UUID.randomUUID(), CREATED);
        insertMetadata(id, "reason", "person@example.com", 0);

        assertThatThrownBy(() -> records.findById(AuditRecordId.from(id)))
                .isInstanceOf(AuditPersistenceException.class)
                .hasMessageNotContaining("person@example.com");
    }

    private static AuditRecordListPage search(
            AuditAction action, AuditActorType actorType, UUID actorId,
            String context, String type, UUID entityId, Instant from, Instant to, int page, int pageSize) {
        return records.search(new AuditSearchRequest(
                action, actorType, actorId == null ? null : SourceEntityId.from(actorId),
                context == null ? null : SourceContext.of(context),
                type == null ? null : SourceType.of(type),
                entityId == null ? null : SourceEntityId.from(entityId),
                from, to, page, pageSize));
    }

    private static void assertIds(AuditRecordListPage page, UUID... ids) {
        assertThat(page.items()).extracting(item -> item.id()).containsExactly(ids);
    }

    private static UUID insertAdmin(
            String action, UUID actorId, String context, String type, UUID entityId, Instant occurred) {
        UUID id = UUID.randomUUID();
        insertRoot(id, action, "ADMIN", "iam", "admin_account", actorId, "Jane Admin",
                context, type, entityId, occurred);
        return id;
    }

    private static void insertSystem(
            UUID id, String action, String context, String type, UUID entityId, Instant occurred) {
        insertRoot(id, action, "SYSTEM", null, null, null, "System", context, type, entityId, occurred);
    }

    private static void insertRoot(
            UUID id, String action, String actorType, String actorContext, String actorSourceType,
            UUID actorId, String actorDisplay, String context, String type, UUID entityId, Instant occurred) {
        jdbc.update("""
                INSERT INTO audit.audit_records (
                    id, action, actor_type, actor_context, actor_source_type, actor_id, actor_display,
                    entity_context, entity_type, entity_id, request_id, occurred_at, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?)
                """, id, action, actorType, actorContext, actorSourceType, actorId, actorDisplay,
                context, type, entityId, Timestamp.from(occurred), Timestamp.from(CREATED));
    }

    private static void insertChange(UUID id, String field, String oldValue, String newValue, int position) {
        jdbc.update("""
                INSERT INTO audit.audit_record_changes
                    (id, audit_record_id, field_path, old_value, new_value, position)
                VALUES (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), id, field, oldValue, newValue, position);
    }

    private static void insertMetadata(UUID id, String key, String value, int position) {
        jdbc.update("""
                INSERT INTO audit.audit_record_metadata
                    (id, audit_record_id, metadata_key, metadata_value, position)
                VALUES (?, ?, ?, ?, ?)
                """, UUID.randomUUID(), id, key, value, position);
    }
}
