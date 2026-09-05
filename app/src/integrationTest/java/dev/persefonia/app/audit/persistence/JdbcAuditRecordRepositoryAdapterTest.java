package dev.persefonia.app.audit.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.audit.domain.record.AuditAction;
import dev.persefonia.audit.domain.record.AuditActorRef;
import dev.persefonia.audit.domain.record.AuditChange;
import dev.persefonia.audit.domain.record.AuditMetadataEntry;
import dev.persefonia.audit.domain.record.AuditRecord;
import dev.persefonia.audit.domain.record.AuditRecordId;
import dev.persefonia.audit.domain.record.AuditedEntityRef;
import dev.persefonia.audit.domain.record.DisplayName;
import dev.persefonia.audit.domain.record.FieldPath;
import dev.persefonia.audit.domain.record.MetadataKey;
import dev.persefonia.audit.domain.record.SafeAuditValue;
import dev.persefonia.audit.domain.record.SafeMetadataValue;
import dev.persefonia.audit.domain.record.SourceContext;
import dev.persefonia.audit.domain.record.SourceEntityId;
import dev.persefonia.audit.domain.record.SourceType;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import dev.persefonia.app.testsupport.SharedPostgresTestServer;

class JdbcAuditRecordRepositoryAdapterTest {
    private static final Instant CREATED_AT = Instant.parse("2026-06-25T10:00:05Z");
    private static final SharedPostgresTestServer.Database POSTGRES = SharedPostgresTestServer.integrationDatabase();
    private static JdbcTemplate jdbc;
    private static JdbcAuditRecordRepositoryAdapter adapter;

    @BeforeAll
    static void migrate() {        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("namedParameterJdbcTemplate", new NamedParameterJdbcTemplate(dataSource));
        adapter = new JdbcAuditRecordRepositoryAdapter(beans.getBeanProvider(NamedParameterJdbcTemplate.class));
    }

    @AfterAll
    static void stopDatabase() {    }

    @BeforeEach
    void clearAuditTables() {
        jdbc.execute("TRUNCATE audit.audit_records CASCADE");
    }

    @Test
    void appendPersistsRootChangesAndMetadataInOrder() {
        AuditRecord record = adminRecord(Instant.parse("2026-06-25T10:00:00Z"), List.of(
                AuditChange.of(FieldPath.of("status"), SafeAuditValue.of("DRAFT"), SafeAuditValue.of("PUBLISHED")),
                AuditChange.of(FieldPath.of("title"), null, SafeAuditValue.of("New title"))),
                List.of(AuditMetadataEntry.of(MetadataKey.of("reason"), SafeMetadataValue.of("scheduled"))));

        adapter.append(record);

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit.audit_records WHERE id = ?", Integer.class, record.id().value()))
                .isEqualTo(1);
        assertThat(jdbc.queryForList("""
                SELECT field_path FROM audit.audit_record_changes
                WHERE audit_record_id = ? ORDER BY position
                """, String.class, record.id().value())).containsExactly("status", "title");
        assertThat(jdbc.queryForList("""
                SELECT metadata_key FROM audit.audit_record_metadata
                WHERE audit_record_id = ? ORDER BY position
                """, String.class, record.id().value())).containsExactly("reason");
    }

    @Test
    void findByIdRehydratesCompleteRecord() {
        AuditRecord record = adminRecord(Instant.parse("2026-06-25T10:00:00Z"), List.of(
                AuditChange.of(FieldPath.of("status"), SafeAuditValue.of("DRAFT"), SafeAuditValue.of("PUBLISHED")),
                AuditChange.of(FieldPath.of("title"), null, SafeAuditValue.of("New title"))),
                List.of(AuditMetadataEntry.of(MetadataKey.of("reason"), SafeMetadataValue.of("scheduled"))));
        adapter.append(record);

        AuditRecord found = adapter.findById(record.id()).orElseThrow();

        assertThat(found.action().value()).isEqualTo("content.published");
        assertThat(found.actor().display().value()).isEqualTo("Jane Admin");
        assertThat(found.actor().context()).map(SourceContext::value).contains("iam");
        assertThat(found.changes())
                .extracting(change -> change.fieldPath().value())
                .containsExactly("status", "title");
        assertThat(found.changes().get(1).oldValueOptional()).isEmpty();
        assertThat(found.metadata())
                .extracting(entry -> entry.key().value())
                .containsExactly("reason");
    }

    @Test
    void communicationIdentifiersRoundTripWithChangesAndMetadata() {
        AuditRecord record = AuditRecord.create(
                AuditRecordId.newId(),
                AuditAction.of("contact_message.status.changed"),
                AuditActorRef.admin(
                        SourceContext.of("iam"),
                        SourceType.of("admin_account"),
                        SourceEntityId.from(UUID.randomUUID()),
                        DisplayName.of("Jane Admin")),
                AuditedEntityRef.of("communication", "contact_message", UUID.randomUUID()),
                null,
                Instant.parse("2026-06-25T10:00:00Z"),
                CREATED_AT,
                List.of(AuditChange.of(
                        FieldPath.of("status"), SafeAuditValue.of("NEW"), SafeAuditValue.of("READ"))),
                List.of(AuditMetadataEntry.of(
                        MetadataKey.of("source.channel"), SafeMetadataValue.of("admin"))));

        adapter.append(record);

        AuditRecord found = adapter.findById(record.id()).orElseThrow();
        assertThat(found.action().value()).isEqualTo("contact_message.status.changed");
        assertThat(found.entity().context().value()).isEqualTo("communication");
        assertThat(found.entity().type().value()).isEqualTo("contact_message");
        assertThat(found.changes()).singleElement().satisfies(change -> {
            assertThat(change.fieldPath().value()).isEqualTo("status");
            assertThat(change.oldValue().value()).isEqualTo("NEW");
            assertThat(change.newValue().value()).isEqualTo("READ");
        });
        assertThat(found.metadata()).singleElement().satisfies(entry -> {
            assertThat(entry.key().value()).isEqualTo("source.channel");
            assertThat(entry.value().value()).isEqualTo("admin");
        });
    }

    @Test
    void findByIdReturnsEmptyForMissingId() {
        assertThat(adapter.findById(AuditRecordId.newId())).isEmpty();
    }

    @Test
    void findRecentReturnsNewestFirst() {
        AuditRecord older = systemRecord(Instant.parse("2026-06-25T09:00:00Z"));
        AuditRecord newer = systemRecord(Instant.parse("2026-06-25T11:00:00Z"));
        adapter.append(older);
        adapter.append(newer);

        List<AuditRecord> recent = adapter.findRecent(10);

        assertThat(recent).extracting(record -> record.id().value())
                .containsExactly(newer.id().value(), older.id().value());
    }

    @Test
    void findRecentCapsLimitAtHundred() {
        for (int index = 0; index < 101; index++) {
            adapter.append(systemRecord(Instant.parse("2026-06-25T09:00:00Z").plusSeconds(index)));
        }

        assertThat(adapter.findRecent(1000)).hasSize(100);
    }

    @Test
    void duplicateAuditRecordIdFails() {
        AuditRecord record = systemRecord(Instant.parse("2026-06-25T10:00:00Z"));
        adapter.append(record);

        assertThatThrownBy(() -> adapter.append(record))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void appendDoesNotRequireSourceContextRows() {
        // The entity reference points at an id with no matching row in any source
        // context; append still succeeds because there is no physical FK.
        AuditRecord record = systemRecord(Instant.parse("2026-06-25T10:00:00Z"));

        adapter.append(record);

        assertThat(adapter.findById(record.id())).isPresent();
    }

    @Test
    void adapterExposesNoUpdateOrDeleteBehavior() {
        List<String> mutating = java.util.Arrays.stream(JdbcAuditRecordRepositoryAdapter.class.getDeclaredMethods())
                .map(Method::getName)
                .map(String::toLowerCase)
                .filter(name -> name.contains("update")
                        || name.contains("delete")
                        || name.contains("remove")
                        || name.contains("replace")
                        || name.contains("upsert")
                        || name.contains("archive"))
                .toList();

        assertThat(mutating).isEmpty();
    }

    @Test
    void adapterDependsOnlyOnJdbcProvider() {
        assertThat(JdbcAuditRecordRepositoryAdapter.class.getDeclaredConstructors()).hasSize(1);
        assertThat(JdbcAuditRecordRepositoryAdapter.class.getDeclaredConstructors()[0].getParameterCount())
                .isEqualTo(1);
    }

    private static AuditRecord adminRecord(Instant occurredAt, List<AuditChange> changes, List<AuditMetadataEntry> metadata) {
        return AuditRecord.create(
                AuditRecordId.newId(),
                AuditAction.of("content.published"),
                AuditActorRef.admin(
                        SourceContext.of("iam"),
                        SourceType.of("admin_account"),
                        SourceEntityId.from(UUID.randomUUID()),
                        DisplayName.of("Jane Admin")),
                AuditedEntityRef.of("publishing", "content_item", UUID.randomUUID()),
                null,
                occurredAt,
                CREATED_AT,
                changes,
                metadata);
    }

    private static AuditRecord systemRecord(Instant occurredAt) {
        return AuditRecord.create(
                AuditRecordId.newId(),
                AuditAction.of("content.unpublished"),
                AuditActorRef.system(DisplayName.of("System")),
                AuditedEntityRef.of("publishing", "content_item", UUID.randomUUID()),
                null,
                occurredAt,
                CREATED_AT,
                List.of(AuditChange.of(FieldPath.of("status"), SafeAuditValue.of("PUBLISHED"), SafeAuditValue.of("DRAFT"))),
                List.of());
    }
}
