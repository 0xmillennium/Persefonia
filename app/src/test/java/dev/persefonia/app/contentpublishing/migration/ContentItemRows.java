package dev.persefonia.app.contentpublishing.migration;

import java.sql.SQLException;
import java.util.UUID;

final class ContentItemRows {
    private ContentItemRows() {
    }

    static UUID insert(ContentItemRow row) throws SQLException {
        PublishingSql.update("""
                INSERT INTO publishing.content_items (
                    id, type, status, visibility, language, slug, title, summary, markdown_source,
                    meta_title, meta_description, canonical_path, og_title, og_description,
                    og_image_asset_id, published_at, unpublished_at, created_at, updated_at, version
                ) VALUES (
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
                )
                """,
                row.id(),
                row.type(),
                row.status(),
                row.visibility(),
                row.language(),
                row.slug(),
                row.title(),
                row.summary(),
                row.markdownSource(),
                row.metaTitle(),
                row.metaDescription(),
                row.canonicalPath(),
                row.ogTitle(),
                row.ogDescription(),
                row.ogImageAssetId(),
                row.publishedAt(),
                row.unpublishedAt(),
                row.createdAt(),
                row.updatedAt(),
                row.version());
        return row.id();
    }

    static UUID insertValidDraft() throws SQLException {
        return insert(ContentItemRow.validDraft());
    }
}
