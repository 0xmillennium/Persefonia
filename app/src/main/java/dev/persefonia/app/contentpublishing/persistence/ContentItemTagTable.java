package dev.persefonia.app.contentpublishing.persistence;

import dev.persefonia.contentpublishing.domain.content.TagId;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

final class ContentItemTagTable {
    private final NamedParameterJdbcTemplate jdbc;

    ContentItemTagTable(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    Set<TagId> findByContentItemId(UUID contentItemId) {
        Objects.requireNonNull(contentItemId, "contentItemId");
        return jdbc.query("""
                SELECT tag_id
                FROM publishing.content_item_tags
                WHERE content_item_id = :contentItemId
                ORDER BY tag_id
                """, Map.of("contentItemId", contentItemId), (resultSet, rowNumber) ->
                TagId.from(resultSet.getObject("tag_id", UUID.class)))
                .stream()
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    void synchronize(UUID contentItemId, Set<TagId> desiredTagIds, Instant assignedAt) {
        Objects.requireNonNull(contentItemId, "contentItemId");
        Objects.requireNonNull(desiredTagIds, "desiredTagIds");
        Objects.requireNonNull(assignedAt, "assignedAt");

        Set<TagId> currentTagIds = findByContentItemId(contentItemId);
        Set<TagId> removedTagIds = new LinkedHashSet<>(currentTagIds);
        removedTagIds.removeAll(desiredTagIds);
        for (TagId removedTagId : removedTagIds) {
            jdbc.update("""
                    DELETE FROM publishing.content_item_tags
                    WHERE content_item_id = :contentItemId
                      AND tag_id = :tagId
                    """, Map.of(
                    "contentItemId", contentItemId,
                    "tagId", removedTagId.value()));
        }

        Set<TagId> addedTagIds = new LinkedHashSet<>(desiredTagIds);
        addedTagIds.removeAll(currentTagIds);
        if (addedTagIds.isEmpty()) {
            return;
        }
        MapSqlParameterSource[] batch = addedTagIds.stream()
                .map(tagId -> new MapSqlParameterSource()
                        .addValue("contentItemId", contentItemId)
                        .addValue("tagId", tagId.value())
                        .addValue("assignedAt", Timestamp.from(assignedAt)))
                .toArray(MapSqlParameterSource[]::new);
        jdbc.batchUpdate("""
                INSERT INTO publishing.content_item_tags (content_item_id, tag_id, assigned_at)
                VALUES (:contentItemId, :tagId, :assignedAt)
                ON CONFLICT (content_item_id, tag_id) DO NOTHING
                """, batch);
    }
}
