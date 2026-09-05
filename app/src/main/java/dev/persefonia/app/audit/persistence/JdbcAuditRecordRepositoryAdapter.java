package dev.persefonia.app.audit.persistence;

import dev.persefonia.audit.domain.record.AuditAction;
import dev.persefonia.audit.domain.record.AuditActorRef;
import dev.persefonia.audit.domain.record.AuditActorType;
import dev.persefonia.audit.domain.record.AuditChange;
import dev.persefonia.audit.domain.record.AuditMetadataEntry;
import dev.persefonia.audit.domain.record.AuditRecord;
import dev.persefonia.audit.domain.record.AuditRecordId;
import dev.persefonia.audit.domain.record.AuditedEntityRef;
import dev.persefonia.audit.domain.record.DisplayName;
import dev.persefonia.audit.domain.record.FieldPath;
import dev.persefonia.audit.domain.record.MetadataKey;
import dev.persefonia.audit.domain.record.RequestId;
import dev.persefonia.audit.domain.record.SafeAuditValue;
import dev.persefonia.audit.domain.record.SafeMetadataValue;
import dev.persefonia.audit.domain.record.SourceContext;
import dev.persefonia.audit.domain.record.SourceEntityId;
import dev.persefonia.audit.domain.record.SourceType;
import dev.persefonia.audit.domain.record.port.AuditRecordRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Append-only JDBC persistence for the audit aggregate. Append participates in
 * the caller's transaction when one exists. There is no upsert, update, delete,
 * or replace, and no source-context repository is consulted. Audit values and SQL
 * parameter values are never logged.
 */
@Repository
public class JdbcAuditRecordRepositoryAdapter implements AuditRecordRepository {
    private static final int MAX_RECENT_LIMIT = 100;

    private final ObjectProvider<NamedParameterJdbcTemplate> jdbc;

    JdbcAuditRecordRepositoryAdapter(ObjectProvider<NamedParameterJdbcTemplate> jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    @Transactional
    public void append(AuditRecord record) {
        Objects.requireNonNull(record, "record");
        insertRoot(record);
        insertChanges(record);
        insertMetadata(record);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AuditRecord> findById(AuditRecordId id) {
        Objects.requireNonNull(id, "id");
        List<AuditRecordRootRow> roots = jdbc().query("""
                SELECT id, action, actor_type, actor_context, actor_source_type, actor_id, actor_display,
                       entity_context, entity_type, entity_id, request_id, occurred_at, created_at
                FROM audit.audit_records
                WHERE id = :id
                """, Map.of("id", id.value()), this::mapRoot);
        return hydrate(roots).stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditRecord> findRecent(int limit) {
        int bounded = Math.min(Math.max(limit, 1), MAX_RECENT_LIMIT);
        return hydrate(jdbc().query("""
                SELECT id, action, actor_type, actor_context, actor_source_type, actor_id, actor_display,
                       entity_context, entity_type, entity_id, request_id, occurred_at, created_at
                FROM audit.audit_records
                ORDER BY occurred_at DESC, id DESC
                LIMIT :limit
                """, Map.of("limit", bounded), this::mapRoot));
    }

    private void insertRoot(AuditRecord record) {
        AuditActorRef actor = record.actor();
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("id", record.id().value())
                .addValue("action", record.action().value())
                .addValue("actorType", actor.type().name())
                .addValue("actorContext", actor.context().map(SourceContext::value).orElse(null))
                .addValue("actorSourceType", actor.sourceType().map(SourceType::value).orElse(null))
                .addValue("actorId", actor.id().map(SourceEntityId::value).orElse(null))
                .addValue("actorDisplay", actor.display().value())
                .addValue("entityContext", record.entity().context().value())
                .addValue("entityType", record.entity().type().value())
                .addValue("entityId", record.entity().id().value())
                .addValue("requestId", record.requestId().map(RequestId::value).orElse(null))
                .addValue("occurredAt", Timestamp.from(record.occurredAt()))
                .addValue("createdAt", Timestamp.from(record.createdAt()));
        jdbc().update("""
                INSERT INTO audit.audit_records (
                    id, action, actor_type, actor_context, actor_source_type, actor_id, actor_display,
                    entity_context, entity_type, entity_id, request_id, occurred_at, created_at
                ) VALUES (
                    :id, :action, :actorType, :actorContext, :actorSourceType, :actorId, :actorDisplay,
                    :entityContext, :entityType, :entityId, :requestId, :occurredAt, :createdAt
                )
                """, parameters);
    }

    private void insertChanges(AuditRecord record) {
        List<AuditChange> changes = record.changes();
        if (changes.isEmpty()) {
            return;
        }
        MapSqlParameterSource[] batch = new MapSqlParameterSource[changes.size()];
        for (int position = 0; position < changes.size(); position++) {
            AuditChange change = changes.get(position);
            batch[position] = new MapSqlParameterSource()
                    .addValue("id", UUID.randomUUID())
                    .addValue("auditRecordId", record.id().value())
                    .addValue("fieldPath", change.fieldPath().value())
                    .addValue("oldValue", change.oldValueOptional().map(SafeAuditValue::value).orElse(null))
                    .addValue("newValue", change.newValueOptional().map(SafeAuditValue::value).orElse(null))
                    .addValue("position", position);
        }
        jdbc().batchUpdate("""
                INSERT INTO audit.audit_record_changes (
                    id, audit_record_id, field_path, old_value, new_value, position
                ) VALUES (
                    :id, :auditRecordId, :fieldPath, :oldValue, :newValue, :position
                )
                """, batch);
    }

    private void insertMetadata(AuditRecord record) {
        List<AuditMetadataEntry> metadata = record.metadata();
        if (metadata.isEmpty()) {
            return;
        }
        MapSqlParameterSource[] batch = new MapSqlParameterSource[metadata.size()];
        for (int position = 0; position < metadata.size(); position++) {
            AuditMetadataEntry entry = metadata.get(position);
            batch[position] = new MapSqlParameterSource()
                    .addValue("id", UUID.randomUUID())
                    .addValue("auditRecordId", record.id().value())
                    .addValue("metadataKey", entry.key().value())
                    .addValue("metadataValue", entry.value().value())
                    .addValue("position", position);
        }
        jdbc().batchUpdate("""
                INSERT INTO audit.audit_record_metadata (
                    id, audit_record_id, metadata_key, metadata_value, position
                ) VALUES (
                    :id, :auditRecordId, :metadataKey, :metadataValue, :position
                )
                """, batch);
    }

    private List<AuditRecord> hydrate(List<AuditRecordRootRow> roots) {
        if (roots.isEmpty()) {
            return List.of();
        }
        List<UUID> recordIds = roots.stream().map(AuditRecordRootRow::id).toList();
        Map<UUID, List<AuditChange>> changes = loadChanges(recordIds);
        Map<UUID, List<AuditMetadataEntry>> metadata = loadMetadata(recordIds);
        List<AuditRecord> records = new ArrayList<>(roots.size());
        for (AuditRecordRootRow root : roots) {
            records.add(AuditRecord.rehydrate(
                    AuditRecordId.from(root.id()),
                    root.action(),
                    root.actor(),
                    root.entity(),
                    root.requestId(),
                    root.occurredAt(),
                    root.createdAt(),
                    changes.getOrDefault(root.id(), List.of()),
                    metadata.getOrDefault(root.id(), List.of())));
        }
        return List.copyOf(records);
    }

    private AuditRecordRootRow mapRoot(ResultSet resultSet, int rowNumber) throws SQLException {
        UUID recordId = resultSet.getObject("id", UUID.class);
        String requestId = resultSet.getString("request_id");
        return new AuditRecordRootRow(
                recordId,
                AuditAction.of(resultSet.getString("action")),
                mapActor(resultSet),
                AuditedEntityRef.of(
                        SourceContext.of(resultSet.getString("entity_context")),
                        SourceType.of(resultSet.getString("entity_type")),
                        SourceEntityId.from(resultSet.getObject("entity_id", UUID.class))),
                requestId == null ? null : RequestId.of(requestId),
                instant(resultSet, "occurred_at"),
                instant(resultSet, "created_at"));
    }

    private static AuditActorRef mapActor(ResultSet resultSet) throws SQLException {
        AuditActorType type = AuditActorType.valueOf(resultSet.getString("actor_type"));
        DisplayName display = DisplayName.of(resultSet.getString("actor_display"));
        return switch (type) {
            case ADMIN -> AuditActorRef.admin(
                    SourceContext.of(resultSet.getString("actor_context")),
                    SourceType.of(resultSet.getString("actor_source_type")),
                    SourceEntityId.from(resultSet.getObject("actor_id", UUID.class)),
                    display);
            case SYSTEM -> AuditActorRef.system(display);
        };
    }

    private Map<UUID, List<AuditChange>> loadChanges(List<UUID> recordIds) {
        Map<UUID, List<AuditChange>> changes = new HashMap<>();
        jdbc().query("""
                SELECT audit_record_id, field_path, old_value, new_value, position
                FROM audit.audit_record_changes
                WHERE audit_record_id IN (:recordIds)
                ORDER BY audit_record_id, position
                """, Map.of("recordIds", recordIds), (resultSet, rowNumber) -> {
            UUID recordId = resultSet.getObject("audit_record_id", UUID.class);
            changes.computeIfAbsent(recordId, ignored -> new ArrayList<>()).add(new AuditChange(
                    FieldPath.of(resultSet.getString("field_path")),
                    nullableAuditValue(resultSet.getString("old_value")),
                    nullableAuditValue(resultSet.getString("new_value"))));
            return null;
        });
        return changes;
    }

    private Map<UUID, List<AuditMetadataEntry>> loadMetadata(List<UUID> recordIds) {
        Map<UUID, List<AuditMetadataEntry>> metadata = new HashMap<>();
        jdbc().query("""
                SELECT audit_record_id, metadata_key, metadata_value, position
                FROM audit.audit_record_metadata
                WHERE audit_record_id IN (:recordIds)
                ORDER BY audit_record_id, position
                """, Map.of("recordIds", recordIds), (resultSet, rowNumber) -> {
            UUID recordId = resultSet.getObject("audit_record_id", UUID.class);
            metadata.computeIfAbsent(recordId, ignored -> new ArrayList<>()).add(new AuditMetadataEntry(
                    MetadataKey.of(resultSet.getString("metadata_key")),
                    SafeMetadataValue.of(resultSet.getString("metadata_value"))));
            return null;
        });
        return metadata;
    }

    private static SafeAuditValue nullableAuditValue(String value) {
        return value == null ? null : SafeAuditValue.of(value);
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getTimestamp(column).toInstant();
    }

    private NamedParameterJdbcTemplate jdbc() {
        NamedParameterJdbcTemplate available = jdbc.getIfAvailable();
        if (available == null) {
            throw new AuditPersistenceException("JDBC audit record repository is not available.");
        }
        return available;
    }

    private record AuditRecordRootRow(
            UUID id,
            AuditAction action,
            AuditActorRef actor,
            AuditedEntityRef entity,
            RequestId requestId,
            Instant occurredAt,
            Instant createdAt) {}
}
