package dev.persefonia.app.contentpublishing.persistence;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table(schema = "publishing", value = "content_items")
record ContentItemPersistenceEntity(
        @Id UUID id,
        String type,
        String status,
        String visibility,
        String language,
        String slug,
        String title,
        String summary,
        @Column("markdown_source") String markdownSource,
        @Column("meta_title") String metaTitle,
        @Column("meta_description") String metaDescription,
        @Column("canonical_path") String canonicalPath,
        @Column("og_title") String ogTitle,
        @Column("og_description") String ogDescription,
        @Column("og_image_asset_id") UUID ogImageAssetId,
        @Column("published_at") Instant publishedAt,
        @Column("unpublished_at") Instant unpublishedAt,
        @Column("created_at") Instant createdAt,
        @Column("updated_at") Instant updatedAt,
        @Version Long version) {
}
