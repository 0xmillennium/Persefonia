package dev.persefonia.app.contentpublishing.persistence;

import dev.persefonia.contentpublishing.domain.content.RenderedHeading;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

final class ContentItemRenderedHeadingTable {
    private final NamedParameterJdbcTemplate jdbc;
    private final RowMapper<Row> rowMapper = this::mapRow;

    ContentItemRenderedHeadingTable(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    void deleteByContentItemId(UUID contentItemId) {
        jdbc.update(
                "DELETE FROM publishing.content_rendered_headings WHERE content_item_id = :contentItemId",
                Map.of("contentItemId", contentItemId));
    }

    void insertAll(UUID contentItemId, List<RenderedHeading> headings) {
        for (RenderedHeading heading : headings) {
            jdbc.update("""
                    INSERT INTO publishing.content_rendered_headings
                        (id, content_item_id, level, text, anchor, position)
                    VALUES
                        (:id, :contentItemId, :level, :text, :anchor, :position)
                    """,
                    new MapSqlParameterSource()
                            .addValue("id", UUID.randomUUID())
                            .addValue("contentItemId", contentItemId)
                            .addValue("level", heading.level().value())
                            .addValue("text", heading.text().value())
                            .addValue("anchor", heading.anchor().value())
                            .addValue("position", heading.position().value()));
        }
    }

    List<Row> findByContentItemId(UUID contentItemId) {
        return jdbc.query("""
                SELECT id, content_item_id, level, text, anchor, position
                FROM publishing.content_rendered_headings
                WHERE content_item_id = :contentItemId
                ORDER BY position ASC
                """,
                Map.of("contentItemId", contentItemId),
                rowMapper);
    }

    private Row mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new Row(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("content_item_id", UUID.class),
                resultSet.getInt("level"),
                resultSet.getString("text"),
                resultSet.getString("anchor"),
                resultSet.getInt("position"));
    }

    record Row(
            UUID id,
            UUID contentItemId,
            int level,
            String text,
            String anchor,
            int position) {
    }
}
