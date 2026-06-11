package dev.persefonia.app.contentpublishing.persistence;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table(schema = "publishing", value = "content_revisions")
record ContentRevisionPersistenceEntity(
        @Id UUID id,
        @Column("content_item_id") UUID contentItemId,
        @Column("revision_number") int revisionNumber,
        @Column("revision_type") String revisionType,
        String title,
        String slug,
        String summary,
        @Column("markdown_source") String markdownSource,
        @Column("rendered_html") String renderedHtml,
        @Column("meta_title") String metaTitle,
        @Column("meta_description") String metaDescription,
        @Column("canonical_path") String canonicalPath,
        @Column("og_title") String ogTitle,
        @Column("og_description") String ogDescription,
        @Column("og_image_asset_id") UUID ogImageAssetId,
        @Column("created_by_admin_ref") UUID createdByAdminRef,
        @Column("created_at") Instant createdAt,
        @Column("change_note") String changeNote) {
}
