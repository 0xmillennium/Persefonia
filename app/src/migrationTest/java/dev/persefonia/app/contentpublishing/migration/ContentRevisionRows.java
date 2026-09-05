package dev.persefonia.app.contentpublishing.migration;

import java.sql.SQLException;

final class ContentRevisionRows {
    private ContentRevisionRows() {
    }

    static void insert(ContentRevisionRow row) throws SQLException {
        PublishingSql.update("""
                INSERT INTO publishing.content_revisions (
                    id, content_item_id, revision_number, revision_type, title, slug, summary,
                    markdown_source, rendered_html, meta_title, meta_description, canonical_path,
                    og_title, og_description, og_image_asset_id, created_by_admin_ref, created_at,
                    change_note
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                row.id(),
                row.contentItemId(),
                row.revisionNumber(),
                row.revisionType(),
                row.title(),
                row.slug(),
                row.summary(),
                row.markdownSource(),
                row.renderedHtml(),
                row.metaTitle(),
                row.metaDescription(),
                row.canonicalPath(),
                row.ogTitle(),
                row.ogDescription(),
                row.ogImageAssetId(),
                row.createdByAdminRef(),
                row.createdAt(),
                row.changeNote());
    }
}
