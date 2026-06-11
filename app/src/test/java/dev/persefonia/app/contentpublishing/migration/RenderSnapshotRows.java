package dev.persefonia.app.contentpublishing.migration;

import java.sql.SQLException;

final class RenderSnapshotRows {
    private RenderSnapshotRows() {
    }

    static void insert(RenderSnapshotRow row) throws SQLException {
        PublishingSql.update("""
                INSERT INTO publishing.content_render_snapshots (
                    content_item_id, rendered_html, rendered_at, renderer_version,
                    reading_time_minutes, contains_mermaid
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                row.contentItemId(),
                row.renderedHtml(),
                row.renderedAt(),
                row.rendererVersion(),
                row.readingTimeMinutes(),
                row.containsMermaid());
    }
}
