package dev.persefonia.app.contentpublishing.migration;

import java.sql.SQLException;

final class RenderedHeadingRows {
    private RenderedHeadingRows() {
    }

    static void insert(RenderedHeadingRow row) throws SQLException {
        PublishingSql.update("""
                INSERT INTO publishing.content_rendered_headings (
                    id, content_item_id, level, text, anchor, position
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                row.id(),
                row.contentItemId(),
                row.level(),
                row.text(),
                row.anchor(),
                row.position());
    }
}
