package dev.persefonia.app.contentpublishing.persistence.spike;

import org.springframework.jdbc.core.JdbcTemplate;

final class SpikeContentPublishingTestSchema {
    private SpikeContentPublishingTestSchema() {
    }

    static void recreate(JdbcTemplate jdbc) {
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS publishing");
        jdbc.execute("DROP TABLE IF EXISTS publishing.content_rendered_headings");
        jdbc.execute("DROP TABLE IF EXISTS publishing.content_revisions");
        jdbc.execute("DROP TABLE IF EXISTS publishing.content_render_snapshots");
        jdbc.execute("DROP TABLE IF EXISTS publishing.content_items");
        jdbc.execute("""
                CREATE TABLE publishing.content_items (
                    id uuid PRIMARY KEY,
                    type text NOT NULL CHECK (type IN ('ARTICLE', 'NOTE', 'RESEARCH', 'PAGE')),
                    status text NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED', 'UNPUBLISHED', 'ARCHIVED')),
                    visibility text NOT NULL CHECK (visibility IN ('PUBLIC', 'UNLISTED', 'PRIVATE')),
                    language text NOT NULL CHECK (language IN ('TR', 'EN')),
                    slug text NULL CHECK (slug IS NULL OR slug ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$'),
                    title text NULL,
                    summary text NULL,
                    markdown_source text NULL,
                    meta_title text NULL,
                    meta_description text NULL,
                    canonical_path text NULL,
                    og_title text NULL,
                    og_description text NULL,
                    og_image_asset_id uuid NULL,
                    published_at timestamptz NULL,
                    unpublished_at timestamptz NULL,
                    created_at timestamptz NOT NULL,
                    updated_at timestamptz NOT NULL,
                    version bigint NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE UNIQUE INDEX content_items_route_namespace_unique
                ON publishing.content_items(type, language, slug)
                WHERE slug IS NOT NULL
                """);
        jdbc.execute("""
                CREATE TABLE publishing.content_render_snapshots (
                    content_item_id uuid PRIMARY KEY
                        REFERENCES publishing.content_items(id) ON DELETE CASCADE,
                    rendered_html text NOT NULL,
                    rendered_at timestamptz NOT NULL,
                    renderer_version text NOT NULL,
                    reading_time_minutes integer NOT NULL,
                    contains_mermaid boolean NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE publishing.content_rendered_headings (
                    id uuid PRIMARY KEY,
                    content_item_id uuid NOT NULL
                        REFERENCES publishing.content_render_snapshots(content_item_id) ON DELETE CASCADE,
                    level integer NOT NULL,
                    text text NOT NULL,
                    anchor text NOT NULL,
                    position integer NOT NULL,
                    UNIQUE (content_item_id, anchor),
                    UNIQUE (content_item_id, position)
                )
                """);
        jdbc.execute("""
                CREATE TABLE publishing.content_revisions (
                    id uuid PRIMARY KEY,
                    content_item_id uuid NOT NULL
                        REFERENCES publishing.content_items(id),
                    revision_number integer NOT NULL,
                    revision_type text NOT NULL,
                    title text NOT NULL,
                    slug text NOT NULL,
                    summary text NOT NULL,
                    markdown_source text NOT NULL,
                    rendered_html text NULL,
                    meta_title text NULL,
                    meta_description text NULL,
                    canonical_path text NULL,
                    og_title text NULL,
                    og_description text NULL,
                    og_image_asset_id uuid NULL,
                    created_by_admin_ref uuid NOT NULL,
                    created_at timestamptz NOT NULL,
                    change_note text NULL,
                    UNIQUE (content_item_id, revision_number)
                )
                """);
    }
}
