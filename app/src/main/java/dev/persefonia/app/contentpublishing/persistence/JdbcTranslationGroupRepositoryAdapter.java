package dev.persefonia.app.contentpublishing.persistence;

import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.content.Version;
import dev.persefonia.contentpublishing.domain.translation.TranslationGroup;
import dev.persefonia.contentpublishing.domain.translation.TranslationGroupEntry;
import dev.persefonia.contentpublishing.domain.translation.TranslationGroupEntryId;
import dev.persefonia.contentpublishing.domain.translation.TranslationGroupId;
import dev.persefonia.contentpublishing.domain.translation.port.TranslationGroupRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class JdbcTranslationGroupRepositoryAdapter implements TranslationGroupRepository {
    private final ObjectProvider<NamedParameterJdbcTemplate> jdbc;
    private final ObjectProvider<TransactionTemplate> transactions;

    JdbcTranslationGroupRepositoryAdapter(
            ObjectProvider<NamedParameterJdbcTemplate> jdbc,
            ObjectProvider<TransactionTemplate> transactions) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    @Override
    public TranslationGroup save(TranslationGroup group) {
        Objects.requireNonNull(group, "group");
        return transactionTemplate().execute(status -> {
            Optional<Long> currentVersion = currentVersion(group.id());
            if (currentVersion.isEmpty()) {
                insertGroup(group);
            } else {
                updateGroup(group, currentVersion.get());
            }
            replaceEntries(group);
            return findById(group.id()).orElseThrow(() -> new ContentPublishingPersistenceException(
                    "Saved translation group could not be reloaded: " + group.id().value()));
        });
    }

    @Override
    public Optional<TranslationGroup> findById(TranslationGroupId id) {
        Objects.requireNonNull(id, "id");
        return loadGroup("id = :id", Map.of("id", id.value()));
    }

    @Override
    public Optional<TranslationGroup> findByContentItemId(ContentId contentItemId) {
        Objects.requireNonNull(contentItemId, "contentItemId");
        List<UUID> groupIds = jdbc().query("""
                SELECT translation_group_id
                FROM publishing.translation_group_entries
                WHERE content_item_id = :contentItemId
                """, Map.of("contentItemId", contentItemId.value()),
                (resultSet, rowNumber) -> resultSet.getObject("translation_group_id", UUID.class));
        if (groupIds.isEmpty()) {
            return Optional.empty();
        }
        return findById(TranslationGroupId.from(groupIds.getFirst()));
    }

    @Override
    public boolean contentItemBelongsToAnyGroup(ContentId contentItemId) {
        Objects.requireNonNull(contentItemId, "contentItemId");
        Long count = jdbc().queryForObject("""
                SELECT count(*)
                FROM publishing.translation_group_entries
                WHERE content_item_id = :contentItemId
                """, Map.of("contentItemId", contentItemId.value()), Long.class);
        return count != null && count > 0;
    }

    private Optional<Long> currentVersion(TranslationGroupId id) {
        List<Long> versions = jdbc().query("""
                SELECT version
                FROM publishing.translation_groups
                WHERE id = :id
                """, Map.of("id", id.value()), (resultSet, rowNumber) -> resultSet.getLong("version"));
        return versions.stream().findFirst();
    }

    private void insertGroup(TranslationGroup group) {
        jdbc().update("""
                INSERT INTO publishing.translation_groups (id, created_at, updated_at, version)
                VALUES (:id, :createdAt, :updatedAt, :version)
                """, new MapSqlParameterSource()
                .addValue("id", group.id().value())
                .addValue("createdAt", Timestamp.from(group.createdAt()))
                .addValue("updatedAt", Timestamp.from(group.updatedAt()))
                .addValue("version", group.version().value()));
    }

    private void updateGroup(TranslationGroup group, long expectedVersion) {
        if (group.version().value() <= expectedVersion) {
            throw new OptimisticLockingFailureException(
                    "Translation group save is stale for id " + group.id().value());
        }
        int updated = jdbc().update("""
                UPDATE publishing.translation_groups
                SET updated_at = :updatedAt, version = :version
                WHERE id = :id AND version = :expectedVersion
                """, new MapSqlParameterSource()
                .addValue("updatedAt", Timestamp.from(group.updatedAt()))
                .addValue("version", group.version().value())
                .addValue("id", group.id().value())
                .addValue("expectedVersion", expectedVersion));
        if (updated != 1) {
            throw new OptimisticLockingFailureException(
                    "Translation group save is stale for id " + group.id().value());
        }
    }

    private void replaceEntries(TranslationGroup group) {
        jdbc().update("""
                DELETE FROM publishing.translation_group_entries
                WHERE translation_group_id = :groupId
                """, Map.of("groupId", group.id().value()));
        MapSqlParameterSource[] batch = group.entries().stream()
                .map(entry -> new MapSqlParameterSource()
                        .addValue("id", entry.id().value())
                        .addValue("groupId", group.id().value())
                        .addValue("contentItemId", entry.contentItemId().value())
                        .addValue("language", entry.language().name())
                        .addValue("contentType", entry.contentType().name())
                        .addValue("addedAt", Timestamp.from(entry.addedAt())))
                .toArray(MapSqlParameterSource[]::new);
        jdbc().batchUpdate("""
                INSERT INTO publishing.translation_group_entries (
                    id, translation_group_id, content_item_id, language, content_type, added_at
                ) VALUES (:id, :groupId, :contentItemId, :language, :contentType, :addedAt)
                """, batch);
    }

    private Optional<TranslationGroup> loadGroup(String whereClause, Map<String, Object> params) {
        String sql = "SELECT id, created_at, updated_at, version "
                + "FROM publishing.translation_groups WHERE " + whereClause;
        List<GroupRow> rows = jdbc().query(sql, params, (resultSet, rowNumber) -> new GroupRow(
                resultSet.getObject("id", UUID.class),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant(),
                resultSet.getLong("version")));
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        GroupRow group = rows.getFirst();
        List<TranslationGroupEntry> entries = loadEntries(group.id());
        return Optional.of(TranslationGroup.rehydrate(
                TranslationGroupId.from(group.id()),
                entries,
                group.createdAt(),
                group.updatedAt(),
                Version.of(group.version())));
    }

    private List<TranslationGroupEntry> loadEntries(UUID groupId) {
        return jdbc().query("""
                SELECT id, content_item_id, language, content_type, added_at
                FROM publishing.translation_group_entries
                WHERE translation_group_id = :groupId
                ORDER BY added_at, id
                """, Map.of("groupId", groupId), (resultSet, rowNumber) -> new TranslationGroupEntry(
                TranslationGroupEntryId.from(resultSet.getObject("id", UUID.class)),
                ContentId.from(resultSet.getObject("content_item_id", UUID.class)),
                ContentLanguage.valueOf(resultSet.getString("language")),
                ContentType.valueOf(resultSet.getString("content_type")),
                resultSet.getTimestamp("added_at").toInstant()));
    }

    private NamedParameterJdbcTemplate jdbc() {
        NamedParameterJdbcTemplate available = jdbc.getIfAvailable();
        if (available == null) {
            throw new ContentPublishingPersistenceException("JDBC translation group repository is not available.");
        }
        return available;
    }

    private TransactionTemplate transactionTemplate() {
        TransactionTemplate available = transactions.getIfAvailable();
        if (available == null) {
            throw new ContentPublishingPersistenceException("JDBC transaction infrastructure is not available.");
        }
        return available;
    }

    private record GroupRow(UUID id, Instant createdAt, Instant updatedAt, long version) {
    }
}
