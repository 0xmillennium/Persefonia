package dev.persefonia.app.contentpublishing.persistence.spike;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

final class SpikeContentPersistenceAdapter {
    private final JdbcAggregateTemplate aggregates;
    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final SpikeContentItemMapper mapper;
    private final RowMapper<SpikeContentRenderSnapshot> snapshotMapper = this::mapSnapshot;
    private final RowMapper<SpikeRenderedHeading> headingMapper = this::mapHeading;
    private final RowMapper<SpikeContentRevision> revisionMapper = this::mapRevision;

    SpikeContentPersistenceAdapter(
            JdbcAggregateTemplate aggregates,
            NamedParameterJdbcTemplate jdbc,
            TransactionTemplate transactions,
            SpikeContentItemMapper mapper) {
        this.aggregates = aggregates;
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.mapper = mapper;
    }

    SpikeContentItem saveContentItem(SpikeContentItem item) {
        return transactions.execute(status -> {
            SpikeContentItemEntity saved = aggregates.save(mapper.toItemEntity(item));
            replaceRenderSnapshot(saved.id(), item.renderSnapshot());
            return findContentItem(saved.id()).orElseThrow();
        });
    }

    Optional<SpikeContentItem> findContentItem(UUID id) {
        SpikeContentItemEntity entity = aggregates.findById(id, SpikeContentItemEntity.class);
        if (entity == null) {
            return Optional.empty();
        }
        return Optional.of(mapper.toItem(entity, findRenderSnapshot(id).orElse(null)));
    }

    void insertRevision(SpikeContentRevision revision) {
        aggregates.insert(mapper.toRevisionEntity(revision));
    }

    Optional<SpikeContentRevision> findRevision(UUID id) {
        SpikeContentRevisionEntity entity = aggregates.findById(id, SpikeContentRevisionEntity.class);
        if (entity == null) {
            return Optional.empty();
        }
        return Optional.of(mapper.toRevision(entity));
    }

    List<SpikeContentRevision> findRevisionsByContentItemId(UUID contentItemId) {
        return jdbc.query("""
                SELECT *
                FROM publishing.content_revisions
                WHERE content_item_id = :contentItemId
                ORDER BY revision_number
                """,
                Map.of("contentItemId", contentItemId),
                revisionMapper);
    }

    List<UUID> findEligibleRouteIds(SpikeContentType type, SpikeLanguage language, Instant now) {
        return jdbc.query("""
                SELECT id
                FROM publishing.content_items
                WHERE type = :type
                  AND language = :language
                  AND slug IS NOT NULL
                  AND status = 'PUBLISHED'
                  AND visibility IN ('PUBLIC', 'UNLISTED')
                  AND published_at IS NOT NULL
                  AND published_at <= :now
                  AND (unpublished_at IS NULL OR unpublished_at > :now)
                ORDER BY slug
                """,
                new MapSqlParameterSource()
                        .addValue("type", type.name())
                        .addValue("language", language.name())
                        .addValue("now", Timestamp.from(now)),
                (rs, rowNum) -> rs.getObject("id", UUID.class));
    }

    private void replaceRenderSnapshot(UUID contentItemId, SpikeContentRenderSnapshot snapshot) {
        jdbc.update(
                "DELETE FROM publishing.content_render_snapshots WHERE content_item_id = :contentItemId",
                Map.of("contentItemId", contentItemId));
        if (snapshot == null) {
            return;
        }
        jdbc.update("""
                INSERT INTO publishing.content_render_snapshots
                    (content_item_id, rendered_html, rendered_at, renderer_version, reading_time_minutes, contains_mermaid)
                VALUES
                    (:contentItemId, :renderedHtml, :renderedAt, :rendererVersion, :readingTimeMinutes, :containsMermaid)
                """,
                new MapSqlParameterSource()
                        .addValue("contentItemId", contentItemId)
                        .addValue("renderedHtml", snapshot.renderedHtml())
                        .addValue("renderedAt", Timestamp.from(snapshot.renderedAt()))
                        .addValue("rendererVersion", snapshot.rendererVersion())
                        .addValue("readingTimeMinutes", snapshot.readingTimeMinutes())
                        .addValue("containsMermaid", snapshot.containsMermaid()));
        for (SpikeRenderedHeading heading : snapshot.headings()) {
            jdbc.update("""
                    INSERT INTO publishing.content_rendered_headings
                        (id, content_item_id, level, text, anchor, position)
                    VALUES
                        (:id, :contentItemId, :level, :text, :anchor, :position)
                    """,
                    new MapSqlParameterSource()
                            .addValue("id", heading.id())
                            .addValue("contentItemId", contentItemId)
                            .addValue("level", heading.level())
                            .addValue("text", heading.text())
                            .addValue("anchor", heading.anchor())
                            .addValue("position", heading.position()));
        }
    }

    private Optional<SpikeContentRenderSnapshot> findRenderSnapshot(UUID contentItemId) {
        List<SpikeContentRenderSnapshot> snapshots = jdbc.query("""
                SELECT *
                FROM publishing.content_render_snapshots
                WHERE content_item_id = :contentItemId
                """,
                Map.of("contentItemId", contentItemId),
                snapshotMapper);
        return snapshots.stream().findFirst();
    }

    private SpikeContentRenderSnapshot mapSnapshot(ResultSet rs, int rowNumber) throws SQLException {
        UUID contentItemId = rs.getObject("content_item_id", UUID.class);
        return new SpikeContentRenderSnapshot(
                rs.getString("rendered_html"),
                instant(rs, "rendered_at"),
                rs.getString("renderer_version"),
                rs.getInt("reading_time_minutes"),
                rs.getBoolean("contains_mermaid"),
                findHeadings(contentItemId));
    }

    private List<SpikeRenderedHeading> findHeadings(UUID contentItemId) {
        return jdbc.query("""
                SELECT *
                FROM publishing.content_rendered_headings
                WHERE content_item_id = :contentItemId
                ORDER BY position
                """,
                Map.of("contentItemId", contentItemId),
                headingMapper);
    }

    private SpikeRenderedHeading mapHeading(ResultSet rs, int rowNumber) throws SQLException {
        return new SpikeRenderedHeading(
                rs.getObject("id", UUID.class),
                rs.getInt("level"),
                rs.getString("text"),
                rs.getString("anchor"),
                rs.getInt("position"));
    }

    private SpikeContentRevision mapRevision(ResultSet rs, int rowNumber) throws SQLException {
        return new SpikeContentRevision(
                rs.getObject("id", UUID.class),
                rs.getObject("content_item_id", UUID.class),
                rs.getInt("revision_number"),
                SpikeRevisionType.valueOf(rs.getString("revision_type")),
                rs.getString("title"),
                rs.getString("slug"),
                rs.getString("summary"),
                rs.getString("markdown_source"),
                rs.getString("rendered_html"),
                rs.getString("meta_title"),
                rs.getString("meta_description"),
                rs.getString("canonical_path"),
                rs.getString("og_title"),
                rs.getString("og_description"),
                rs.getObject("og_image_asset_id", UUID.class),
                rs.getObject("created_by_admin_ref", UUID.class),
                instant(rs, "created_at"),
                rs.getString("change_note"));
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
