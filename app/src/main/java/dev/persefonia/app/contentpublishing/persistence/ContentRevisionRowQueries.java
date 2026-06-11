package dev.persefonia.app.contentpublishing.persistence;

import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.revision.RevisionNumber;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

final class ContentRevisionRowQueries {
    private final NamedParameterJdbcTemplate jdbc;
    private final RowMapper<ContentRevisionPersistenceEntity> rowMapper = this::mapRow;

    ContentRevisionRowQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<ContentRevisionPersistenceEntity> findByContentId(ContentId contentId) {
        return jdbc.query("""
                SELECT *
                FROM publishing.content_revisions
                WHERE content_item_id = :contentItemId
                ORDER BY revision_number ASC
                """,
                Map.of("contentItemId", contentId.value()),
                rowMapper);
    }

    Optional<RevisionNumber> findLatestRevisionNumber(ContentId contentId) {
        Integer latest = jdbc.queryForObject("""
                SELECT max(revision_number)
                FROM publishing.content_revisions
                WHERE content_item_id = :contentItemId
                """,
                Map.of("contentItemId", contentId.value()),
                Integer.class);
        return latest == null ? Optional.empty() : Optional.of(RevisionNumber.of(latest));
    }

    private ContentRevisionPersistenceEntity mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ContentRevisionPersistenceEntity(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("content_item_id", UUID.class),
                resultSet.getInt("revision_number"),
                resultSet.getString("revision_type"),
                resultSet.getString("title"),
                resultSet.getString("slug"),
                resultSet.getString("summary"),
                resultSet.getString("markdown_source"),
                resultSet.getString("rendered_html"),
                resultSet.getString("meta_title"),
                resultSet.getString("meta_description"),
                resultSet.getString("canonical_path"),
                resultSet.getString("og_title"),
                resultSet.getString("og_description"),
                resultSet.getObject("og_image_asset_id", UUID.class),
                resultSet.getObject("created_by_admin_ref", UUID.class),
                instant(resultSet, "created_at"),
                resultSet.getString("change_note"));
    }

    private Instant instant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
