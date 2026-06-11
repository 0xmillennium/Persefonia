package dev.persefonia.app.contentpublishing.persistence;

import dev.persefonia.contentpublishing.domain.content.ContentRenderSnapshot;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

final class ContentItemRenderSnapshotTable {
    private final NamedParameterJdbcTemplate jdbc;
    private final RowMapper<Row> rowMapper = this::mapRow;

    ContentItemRenderSnapshotTable(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    void delete(UUID contentItemId) {
        jdbc.update(
                "DELETE FROM publishing.content_render_snapshots WHERE content_item_id = :contentItemId",
                Map.of("contentItemId", contentItemId));
    }

    void insert(UUID contentItemId, ContentRenderSnapshot snapshot) {
        jdbc.update("""
                INSERT INTO publishing.content_render_snapshots
                    (content_item_id, rendered_html, rendered_at, renderer_version, reading_time_minutes, contains_mermaid)
                VALUES
                    (:contentItemId, :renderedHtml, :renderedAt, :rendererVersion, :readingTimeMinutes, :containsMermaid)
                """,
                new MapSqlParameterSource()
                        .addValue("contentItemId", contentItemId)
                        .addValue("renderedHtml", snapshot.renderedHtml().value())
                        .addValue("renderedAt", Timestamp.from(snapshot.renderedAt()))
                        .addValue("rendererVersion", snapshot.rendererVersion().value())
                        .addValue("readingTimeMinutes", snapshot.readingTime().minutes())
                        .addValue("containsMermaid", snapshot.containsMermaid()));
    }

    Optional<Row> findByContentItemId(UUID contentItemId) {
        List<Row> rows = jdbc.query("""
                SELECT content_item_id, rendered_html, rendered_at, renderer_version, reading_time_minutes, contains_mermaid
                FROM publishing.content_render_snapshots
                WHERE content_item_id = :contentItemId
                """,
                Map.of("contentItemId", contentItemId),
                rowMapper);
        return rows.stream().findFirst();
    }

    private Row mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new Row(
                resultSet.getObject("content_item_id", UUID.class),
                resultSet.getString("rendered_html"),
                resultSet.getTimestamp("rendered_at").toInstant(),
                resultSet.getString("renderer_version"),
                resultSet.getInt("reading_time_minutes"),
                resultSet.getBoolean("contains_mermaid"));
    }

    record Row(
            UUID contentItemId,
            String renderedHtml,
            Instant renderedAt,
            String rendererVersion,
            int readingTimeMinutes,
            boolean containsMermaid) {
    }
}
