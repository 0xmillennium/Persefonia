CREATE TABLE publishing.content_items (
    id uuid NOT NULL,
    type text NOT NULL,
    status text NOT NULL,
    visibility text NOT NULL,
    language text NOT NULL,
    slug text NULL,
    title text NULL,
    summary text NULL,
    markdown_source text NULL,
    meta_title text NULL,
    meta_description text NULL,
    canonical_path text NULL,
    og_title text NULL,
    og_description text NULL,
    og_image_asset_id uuid NULL,
    published_at timestamp with time zone NULL,
    unpublished_at timestamp with time zone NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT pk_content_items PRIMARY KEY (id),
    CONSTRAINT ck_content_items__type CHECK (type IN ('ARTICLE', 'NOTE', 'RESEARCH', 'PAGE')),
    CONSTRAINT ck_content_items__status CHECK (status IN ('DRAFT', 'PUBLISHED', 'UNPUBLISHED', 'ARCHIVED')),
    CONSTRAINT ck_content_items__visibility CHECK (visibility IN ('PUBLIC', 'UNLISTED', 'PRIVATE')),
    CONSTRAINT ck_content_items__language CHECK (language IN ('TR', 'EN')),
    CONSTRAINT ck_content_items__slug_not_blank CHECK (slug IS NULL OR btrim(slug) <> ''),
    CONSTRAINT ck_content_items__slug_format CHECK (slug IS NULL OR slug ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$'),
    CONSTRAINT ck_content_items__title_not_blank CHECK (title IS NULL OR btrim(title) <> ''),
    CONSTRAINT ck_content_items__summary_not_blank CHECK (summary IS NULL OR btrim(summary) <> ''),
    CONSTRAINT ck_content_items__markdown_source_not_blank CHECK (
        markdown_source IS NULL OR btrim(markdown_source) <> ''
    ),
    CONSTRAINT ck_content_items__canonical_path_not_blank CHECK (
        canonical_path IS NULL OR btrim(canonical_path) <> ''
    ),
    CONSTRAINT ck_content_items__canonical_path_format CHECK (
        canonical_path IS NULL
        OR (
            canonical_path LIKE '/%'
            AND canonical_path !~ '\s'
        )
    ),
    CONSTRAINT ck_content_items__published_complete CHECK (
        status <> 'PUBLISHED'
        OR (
            slug IS NOT NULL
            AND title IS NOT NULL
            AND summary IS NOT NULL
            AND markdown_source IS NOT NULL
            AND canonical_path IS NOT NULL
            AND published_at IS NOT NULL
        )
    ),
    CONSTRAINT ck_content_items__unpublished_requires_publish_timestamps CHECK (
        status <> 'UNPUBLISHED'
        OR (
            published_at IS NOT NULL
            AND unpublished_at IS NOT NULL
        )
    ),
    CONSTRAINT ck_content_items__unpublished_requires_published_at CHECK (
        unpublished_at IS NULL
        OR published_at IS NOT NULL
    ),
    CONSTRAINT ck_content_items__unpublished_not_before_published CHECK (
        unpublished_at IS NULL
        OR unpublished_at >= published_at
    ),
    CONSTRAINT ck_content_items__updated_not_before_created CHECK (updated_at >= created_at),
    CONSTRAINT ck_content_items__version_non_negative CHECK (version >= 0),
    CONSTRAINT ck_content_items__title_length CHECK (title IS NULL OR char_length(title) <= 200),
    CONSTRAINT ck_content_items__summary_length CHECK (summary IS NULL OR char_length(summary) <= 500),
    CONSTRAINT ck_content_items__meta_title_length CHECK (meta_title IS NULL OR char_length(meta_title) <= 200),
    CONSTRAINT ck_content_items__meta_description_length CHECK (
        meta_description IS NULL OR char_length(meta_description) <= 500
    ),
    CONSTRAINT ck_content_items__og_title_length CHECK (og_title IS NULL OR char_length(og_title) <= 200),
    CONSTRAINT ck_content_items__og_description_length CHECK (
        og_description IS NULL OR char_length(og_description) <= 500
    ),
    CONSTRAINT ck_content_items__canonical_path_length CHECK (
        canonical_path IS NULL OR char_length(canonical_path) <= 512
    )
);

CREATE UNIQUE INDEX uq_content_items__route_namespace
    ON publishing.content_items (type, language, slug)
    WHERE slug IS NOT NULL;

CREATE INDEX ix_content_items__status
    ON publishing.content_items (status);

CREATE INDEX ix_content_items__visibility
    ON publishing.content_items (visibility);

CREATE INDEX ix_content_items__language
    ON publishing.content_items (language);

CREATE INDEX ix_content_items__type_status_visibility_language
    ON publishing.content_items (type, status, visibility, language);

CREATE INDEX ix_content_items__published_at
    ON publishing.content_items (published_at);

CREATE INDEX ix_content_items__updated_at
    ON publishing.content_items (updated_at);

CREATE INDEX ix_content_items__route_lookup
    ON publishing.content_items (type, language, slug, status, visibility)
    WHERE slug IS NOT NULL;

CREATE TABLE publishing.content_render_snapshots (
    content_item_id uuid NOT NULL,
    rendered_html text NOT NULL,
    rendered_at timestamp with time zone NOT NULL,
    renderer_version text NOT NULL,
    reading_time_minutes integer NOT NULL,
    contains_mermaid boolean NOT NULL,
    CONSTRAINT pk_content_render_snapshots PRIMARY KEY (content_item_id),
    CONSTRAINT fk_content_render_snapshots__content_items
        FOREIGN KEY (content_item_id)
        REFERENCES publishing.content_items (id)
        ON DELETE CASCADE,
    CONSTRAINT ck_content_render_snapshots__rendered_html_not_blank CHECK (btrim(rendered_html) <> ''),
    CONSTRAINT ck_content_render_snapshots__renderer_version_not_blank CHECK (btrim(renderer_version) <> ''),
    CONSTRAINT ck_content_render_snapshots__reading_time_positive CHECK (reading_time_minutes >= 1)
);

CREATE TABLE publishing.content_rendered_headings (
    id uuid NOT NULL,
    content_item_id uuid NOT NULL,
    level integer NOT NULL,
    text text NOT NULL,
    anchor text NOT NULL,
    position integer NOT NULL,
    CONSTRAINT pk_content_rendered_headings PRIMARY KEY (id),
    CONSTRAINT fk_content_rendered_headings__render_snapshots
        FOREIGN KEY (content_item_id)
        REFERENCES publishing.content_render_snapshots (content_item_id)
        ON DELETE CASCADE,
    CONSTRAINT uq_content_rendered_headings__content_anchor UNIQUE (content_item_id, anchor),
    CONSTRAINT uq_content_rendered_headings__content_position UNIQUE (content_item_id, position),
    CONSTRAINT ck_content_rendered_headings__level CHECK (level BETWEEN 1 AND 6),
    CONSTRAINT ck_content_rendered_headings__text_not_blank CHECK (btrim(text) <> ''),
    CONSTRAINT ck_content_rendered_headings__anchor_not_blank CHECK (btrim(anchor) <> ''),
    CONSTRAINT ck_content_rendered_headings__anchor_format CHECK (anchor ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$'),
    CONSTRAINT ck_content_rendered_headings__position_positive CHECK (position > 0)
);

CREATE INDEX ix_content_rendered_headings__content_position
    ON publishing.content_rendered_headings (content_item_id, position);

CREATE TABLE publishing.content_revisions (
    id uuid NOT NULL,
    content_item_id uuid NOT NULL,
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
    created_at timestamp with time zone NOT NULL,
    change_note text NULL,
    CONSTRAINT pk_content_revisions PRIMARY KEY (id),
    CONSTRAINT fk_content_revisions__content_items
        FOREIGN KEY (content_item_id)
        REFERENCES publishing.content_items (id),
    CONSTRAINT uq_content_revisions__content_revision_number UNIQUE (content_item_id, revision_number),
    CONSTRAINT ck_content_revisions__revision_number_positive CHECK (revision_number > 0),
    CONSTRAINT ck_content_revisions__revision_type CHECK (
        revision_type IN ('PUBLISH', 'MANUAL_SNAPSHOT', 'RESTORE_SOURCE')
    ),
    CONSTRAINT ck_content_revisions__title_not_blank CHECK (btrim(title) <> ''),
    CONSTRAINT ck_content_revisions__slug_not_blank CHECK (btrim(slug) <> ''),
    CONSTRAINT ck_content_revisions__slug_format CHECK (slug ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$'),
    CONSTRAINT ck_content_revisions__summary_not_blank CHECK (btrim(summary) <> ''),
    CONSTRAINT ck_content_revisions__markdown_source_not_blank CHECK (btrim(markdown_source) <> ''),
    CONSTRAINT ck_content_revisions__rendered_html_not_blank CHECK (
        rendered_html IS NULL OR btrim(rendered_html) <> ''
    ),
    CONSTRAINT ck_content_revisions__canonical_path_not_blank CHECK (
        canonical_path IS NULL OR btrim(canonical_path) <> ''
    ),
    CONSTRAINT ck_content_revisions__canonical_path_format CHECK (
        canonical_path IS NULL
        OR (
            canonical_path LIKE '/%'
            AND canonical_path !~ '\s'
        )
    ),
    CONSTRAINT ck_content_revisions__change_note_not_blank CHECK (
        change_note IS NULL OR btrim(change_note) <> ''
    ),
    CONSTRAINT ck_content_revisions__publish_has_rendered_html CHECK (
        revision_type <> 'PUBLISH'
        OR rendered_html IS NOT NULL
    ),
    CONSTRAINT ck_content_revisions__title_length CHECK (char_length(title) <= 200),
    CONSTRAINT ck_content_revisions__summary_length CHECK (char_length(summary) <= 500),
    CONSTRAINT ck_content_revisions__meta_title_length CHECK (
        meta_title IS NULL OR char_length(meta_title) <= 200
    ),
    CONSTRAINT ck_content_revisions__meta_description_length CHECK (
        meta_description IS NULL OR char_length(meta_description) <= 500
    ),
    CONSTRAINT ck_content_revisions__og_title_length CHECK (og_title IS NULL OR char_length(og_title) <= 200),
    CONSTRAINT ck_content_revisions__og_description_length CHECK (
        og_description IS NULL OR char_length(og_description) <= 500
    ),
    CONSTRAINT ck_content_revisions__canonical_path_length CHECK (
        canonical_path IS NULL OR char_length(canonical_path) <= 512
    ),
    CONSTRAINT ck_content_revisions__change_note_length CHECK (
        change_note IS NULL OR char_length(change_note) <= 1000
    )
);

CREATE INDEX ix_content_revisions__content_item_id
    ON publishing.content_revisions (content_item_id);

CREATE INDEX ix_content_revisions__content_revision_number
    ON publishing.content_revisions (content_item_id, revision_number);

CREATE INDEX ix_content_revisions__created_at
    ON publishing.content_revisions (created_at);

CREATE INDEX ix_content_revisions__revision_type
    ON publishing.content_revisions (revision_type);
