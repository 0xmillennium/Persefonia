package dev.persefonia.app.contentpublishing.persistence;

import dev.persefonia.contentpublishing.application.port.ContentTagAssignmentStore;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ReferencedTagId;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcContentTagAssignmentStore implements ContentTagAssignmentStore {
    private final ObjectProvider<NamedParameterJdbcTemplate> jdbc;

    public JdbcContentTagAssignmentStore(ObjectProvider<NamedParameterJdbcTemplate> jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public Set<ReferencedTagId> findAssignedTagIds(ContentId contentId) {
        Objects.requireNonNull(contentId, "contentId");
        return jdbc().query("""
                SELECT tag_id
                FROM publishing.content_item_tags
                WHERE content_item_id = :contentItemId
                ORDER BY tag_id
                """, Map.of("contentItemId", contentId.value()), (resultSet, rowNumber) ->
                ReferencedTagId.from(resultSet.getObject("tag_id", UUID.class)))
                .stream()
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public void replaceAssignedTagIds(ContentId contentId, Set<ReferencedTagId> tagIds, Instant assignedAt) {
        Objects.requireNonNull(contentId, "contentId");
        Objects.requireNonNull(tagIds, "tagIds");
        Objects.requireNonNull(assignedAt, "assignedAt");
        jdbc().update("""
                DELETE FROM publishing.content_item_tags
                WHERE content_item_id = :contentItemId
                """, Map.of("contentItemId", contentId.value()));
        if (tagIds.isEmpty()) {
            return;
        }
        MapSqlParameterSource[] batch = tagIds.stream()
                .map(tagId -> new MapSqlParameterSource()
                        .addValue("contentItemId", contentId.value())
                        .addValue("tagId", tagId.value())
                        .addValue("assignedAt", Timestamp.from(assignedAt)))
                .toArray(MapSqlParameterSource[]::new);
        jdbc().batchUpdate("""
                INSERT INTO publishing.content_item_tags (content_item_id, tag_id, assigned_at)
                VALUES (:contentItemId, :tagId, :assignedAt)
                """, batch);
    }

    private NamedParameterJdbcTemplate jdbc() {
        NamedParameterJdbcTemplate available = jdbc.getIfAvailable();
        if (available == null) {
            throw new ContentPublishingPersistenceException("JDBC content tag assignment store is not available.");
        }
        return available;
    }
}
