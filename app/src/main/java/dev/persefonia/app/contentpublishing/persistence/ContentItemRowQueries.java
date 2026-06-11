package dev.persefonia.app.contentpublishing.persistence;

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

final class ContentItemRowQueries {
    private final NamedParameterJdbcTemplate jdbc;
    private final RowMapper<ContentItemPersistenceEntity> rowMapper = this::mapRow;

    ContentItemRowQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Optional<ContentItemPersistenceEntity> findBySlugAndTypeAndLanguage(String slug, String type, String language) {
        return first(jdbc.query("""
                SELECT *
                FROM publishing.content_items
                WHERE slug = :slug
                  AND type = :type
                  AND language = :language
                LIMIT 1
                """,
                new MapSqlParameterSource()
                        .addValue("slug", slug)
                        .addValue("type", type)
                        .addValue("language", language),
                rowMapper));
    }

    Optional<ContentItemPersistenceEntity> findPublishedByRoute(String type, String slug, String language) {
        return first(jdbc.query("""
                SELECT *
                FROM publishing.content_items
                WHERE type = :type
                  AND language = :language
                  AND slug = :slug
                  AND status = 'PUBLISHED'
                  AND visibility IN ('PUBLIC', 'UNLISTED')
                LIMIT 1
                """,
                new MapSqlParameterSource()
                        .addValue("type", type)
                        .addValue("language", language)
                        .addValue("slug", slug),
                rowMapper));
    }

    List<ContentItemPersistenceEntity> findDrafts() {
        return jdbc.query("""
                SELECT *
                FROM publishing.content_items
                WHERE status = 'DRAFT'
                ORDER BY updated_at DESC, id ASC
                """,
                rowMapper);
    }

    List<ContentItemPersistenceEntity> findByStatus(String status) {
        return jdbc.query("""
                SELECT *
                FROM publishing.content_items
                WHERE status = :status
                ORDER BY updated_at DESC, id ASC
                """,
                Map.of("status", status),
                rowMapper);
    }

    boolean existsSlugInNamespace(String type, String language, String slug) {
        Boolean exists = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM publishing.content_items
                    WHERE type = :type
                      AND language = :language
                      AND slug = :slug
                )
                """,
                new MapSqlParameterSource()
                        .addValue("type", type)
                        .addValue("language", language)
                        .addValue("slug", slug),
                Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    private Optional<ContentItemPersistenceEntity> first(List<ContentItemPersistenceEntity> rows) {
        return rows.stream().findFirst();
    }

    private ContentItemPersistenceEntity mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ContentItemPersistenceEntity(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("type"),
                resultSet.getString("status"),
                resultSet.getString("visibility"),
                resultSet.getString("language"),
                resultSet.getString("slug"),
                resultSet.getString("title"),
                resultSet.getString("summary"),
                resultSet.getString("markdown_source"),
                resultSet.getString("meta_title"),
                resultSet.getString("meta_description"),
                resultSet.getString("canonical_path"),
                resultSet.getString("og_title"),
                resultSet.getString("og_description"),
                resultSet.getObject("og_image_asset_id", UUID.class),
                instant(resultSet, "published_at"),
                instant(resultSet, "unpublished_at"),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"),
                resultSet.getLong("version"));
    }

    private Instant instant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
