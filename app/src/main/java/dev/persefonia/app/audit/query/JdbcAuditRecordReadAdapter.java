package dev.persefonia.app.audit.query;

import dev.persefonia.app.audit.persistence.AuditPersistenceException;
import dev.persefonia.audit.application.port.AuditRecordReadPort;
import dev.persefonia.audit.application.query.AuditChangeView;
import dev.persefonia.audit.application.query.AuditMetadataView;
import dev.persefonia.audit.application.query.AuditRecordDetail;
import dev.persefonia.audit.application.query.AuditRecordListItem;
import dev.persefonia.audit.application.query.AuditRecordListPage;
import dev.persefonia.audit.application.query.AuditSearchRequest;
import dev.persefonia.audit.domain.record.AuditAction;
import dev.persefonia.audit.domain.record.AuditActorRef;
import dev.persefonia.audit.domain.record.AuditActorType;
import dev.persefonia.audit.domain.record.AuditChange;
import dev.persefonia.audit.domain.record.AuditMetadataEntry;
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
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

/** JDBC CQRS read adapter. List reads never touch Audit child tables. */
@Repository
public class JdbcAuditRecordReadAdapter implements AuditRecordReadPort {
    private static final String ROOT_COLUMNS = """
            id, action, actor_type, actor_context, actor_source_type, actor_id, actor_display,
            entity_context, entity_type, entity_id, request_id, occurred_at, created_at
            """;

    private final ObjectProvider<NamedParameterJdbcTemplate> jdbc;

    JdbcAuditRecordReadAdapter(ObjectProvider<NamedParameterJdbcTemplate> jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    @Transactional(readOnly = true)
    public AuditRecordListPage search(AuditSearchRequest request) {
        Objects.requireNonNull(request, "request");
        QueryParts query = queryParts(request);
        long total = jdbc().queryForObject(
                "SELECT COUNT(*) FROM audit.audit_records" + query.whereClause(),
                query.parameters(),
                Long.class);
        query.parameters()
                .addValue("limit", request.pageSize())
                .addValue("offset", ((long) request.page() - 1L) * request.pageSize());
        List<AuditRecordListItem> items = safely(() -> jdbc().query(
                "SELECT " + ROOT_COLUMNS + " FROM audit.audit_records" + query.whereClause()
                        + " ORDER BY occurred_at DESC, id DESC LIMIT :limit OFFSET :offset",
                query.parameters(),
                (resultSet, rowNumber) -> toListItem(root(resultSet))));
        return new AuditRecordListPage(items, request.page(), request.pageSize(), total);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AuditRecordDetail> findById(AuditRecordId id) {
        Objects.requireNonNull(id, "id");
        return safely(() -> jdbc().query(
                "SELECT " + ROOT_COLUMNS + " FROM audit.audit_records WHERE id = :id",
                Map.of("id", id.value()),
                (resultSet, rowNumber) -> root(resultSet)))
                .stream()
                .findFirst()
                .map(root -> new AuditRecordDetail(
                        root.id().value(),
                        root.action().value(),
                        root.actor().type().name(),
                        root.actor().context().map(SourceContext::value).orElse(null),
                        root.actor().sourceType().map(SourceType::value).orElse(null),
                        root.actor().id().map(SourceEntityId::value).orElse(null),
                        root.actor().display().value(),
                        root.entity().context().value(),
                        root.entity().type().value(),
                        root.entity().id().value(),
                        root.requestId() == null ? null : root.requestId().value(),
                        root.occurredAt(),
                        root.createdAt(),
                        changes(root.id().value()),
                        metadata(root.id().value())));
    }

    private static QueryParts queryParts(AuditSearchRequest request) {
        List<String> conditions = new ArrayList<>();
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        request.actionOptional().ifPresent(value -> {
            conditions.add("action = :action");
            parameters.addValue("action", value.value());
        });
        request.actorTypeOptional().ifPresent(value -> {
            conditions.add("actor_type = :actorType");
            parameters.addValue("actorType", value.name());
        });
        request.actorIdOptional().ifPresent(value -> {
            conditions.add("actor_id = :actorId");
            parameters.addValue("actorId", value.value());
        });
        request.entityContextOptional().ifPresent(value -> {
            conditions.add("entity_context = :entityContext");
            parameters.addValue("entityContext", value.value());
        });
        request.entityTypeOptional().ifPresent(value -> {
            conditions.add("entity_type = :entityType");
            parameters.addValue("entityType", value.value());
        });
        request.entityIdOptional().ifPresent(value -> {
            conditions.add("entity_id = :entityId");
            parameters.addValue("entityId", value.value());
        });
        request.occurredFromInclusiveOptional().ifPresent(value -> {
            conditions.add("occurred_at >= :occurredFrom");
            parameters.addValue("occurredFrom", Timestamp.from(value));
        });
        request.occurredToExclusiveOptional().ifPresent(value -> {
            conditions.add("occurred_at < :occurredTo");
            parameters.addValue("occurredTo", Timestamp.from(value));
        });
        return new QueryParts(
                conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions),
                parameters);
    }

    private static AuditRecordListItem toListItem(Root root) {
        return new AuditRecordListItem(
                root.id().value(), root.action().value(), root.actor().type().name(),
                root.actor().display().value(), root.entity().context().value(),
                root.entity().type().value(), root.entity().id().value(), root.occurredAt());
    }

    private Root root(ResultSet resultSet) throws SQLException {
        AuditActorType actorType = AuditActorType.valueOf(resultSet.getString("actor_type"));
        DisplayName actorDisplay = DisplayName.of(resultSet.getString("actor_display"));
        AuditActorRef actor = switch (actorType) {
            case ADMIN -> AuditActorRef.admin(
                    SourceContext.of(resultSet.getString("actor_context")),
                    SourceType.of(resultSet.getString("actor_source_type")),
                    SourceEntityId.from(resultSet.getObject("actor_id", UUID.class)),
                    actorDisplay);
            case SYSTEM -> AuditActorRef.system(actorDisplay);
        };
        String rawRequestId = resultSet.getString("request_id");
        return new Root(
                AuditRecordId.from(resultSet.getObject("id", UUID.class)),
                AuditAction.of(resultSet.getString("action")),
                actor,
                AuditedEntityRef.of(
                        SourceContext.of(resultSet.getString("entity_context")),
                        SourceType.of(resultSet.getString("entity_type")),
                        SourceEntityId.from(resultSet.getObject("entity_id", UUID.class))),
                rawRequestId == null ? null : RequestId.of(rawRequestId),
                resultSet.getTimestamp("occurred_at").toInstant(),
                resultSet.getTimestamp("created_at").toInstant());
    }

    private List<AuditChangeView> changes(UUID recordId) {
        return safely(() -> jdbc().query("""
                SELECT field_path, old_value, new_value
                FROM audit.audit_record_changes
                WHERE audit_record_id = :recordId
                ORDER BY position
                """, Map.of("recordId", recordId), (resultSet, rowNumber) -> {
            AuditChange change = AuditChange.of(
                    FieldPath.of(resultSet.getString("field_path")),
                    nullableAuditValue(resultSet.getString("old_value")),
                    nullableAuditValue(resultSet.getString("new_value")));
            return new AuditChangeView(
                    change.fieldPath().value(),
                    change.oldValueOptional().map(SafeAuditValue::value).orElse(null),
                    change.newValueOptional().map(SafeAuditValue::value).orElse(null));
        }));
    }

    private List<AuditMetadataView> metadata(UUID recordId) {
        return safely(() -> jdbc().query("""
                SELECT metadata_key, metadata_value
                FROM audit.audit_record_metadata
                WHERE audit_record_id = :recordId
                ORDER BY position
                """, Map.of("recordId", recordId), (resultSet, rowNumber) -> {
            AuditMetadataEntry entry = AuditMetadataEntry.of(
                    MetadataKey.of(resultSet.getString("metadata_key")),
                    SafeMetadataValue.of(resultSet.getString("metadata_value")));
            return new AuditMetadataView(entry.key().value(), entry.value().value());
        }));
    }

    private static SafeAuditValue nullableAuditValue(String value) {
        return value == null ? null : SafeAuditValue.of(value);
    }

    private <T> T safely(java.util.function.Supplier<T> query) {
        try {
            return query.get();
        } catch (dev.persefonia.audit.domain.record.AuditValidationException | IllegalArgumentException exception) {
            throw new AuditPersistenceException("Persisted audit data failed safety validation.");
        }
    }

    private NamedParameterJdbcTemplate jdbc() {
        NamedParameterJdbcTemplate available = jdbc.getIfAvailable();
        if (available == null) {
            throw new AuditPersistenceException("JDBC audit read adapter is not available.");
        }
        return available;
    }

    private record QueryParts(String whereClause, MapSqlParameterSource parameters) {}

    private record Root(
            AuditRecordId id,
            AuditAction action,
            AuditActorRef actor,
            AuditedEntityRef entity,
            RequestId requestId,
            Instant occurredAt,
            Instant createdAt) {}
}
