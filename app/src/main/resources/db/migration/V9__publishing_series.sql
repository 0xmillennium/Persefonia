CREATE TABLE publishing.series (
    id uuid NOT NULL,
    language text NOT NULL,
    slug text NOT NULL,
    title text NOT NULL,
    description text NULL,
    status text NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT pk_series PRIMARY KEY (id),
    CONSTRAINT uq_series__language_slug UNIQUE (language, slug),
    CONSTRAINT ck_series__language CHECK (language IN ('TR', 'EN')),
    CONSTRAINT ck_series__status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_series__title_not_blank CHECK (btrim(title) <> ''),
    CONSTRAINT ck_series__slug_not_blank CHECK (btrim(slug) <> ''),
    CONSTRAINT ck_series__slug_format CHECK (slug ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$'),
    CONSTRAINT ck_series__description_not_blank CHECK (description IS NULL OR btrim(description) <> ''),
    CONSTRAINT ck_series__updated_not_before_created CHECK (updated_at >= created_at),
    CONSTRAINT ck_series__version_non_negative CHECK (version >= 0),
    CONSTRAINT ck_series__title_length CHECK (char_length(title) <= 120),
    CONSTRAINT ck_series__description_length CHECK (description IS NULL OR char_length(description) <= 500)
);

CREATE INDEX ix_series_status
    ON publishing.series (status);

CREATE INDEX ix_series_updated_at
    ON publishing.series (updated_at);

CREATE TABLE publishing.series_entries (
    id uuid NOT NULL,
    series_id uuid NOT NULL,
    content_item_id uuid NOT NULL,
    position integer NOT NULL,
    added_at timestamp with time zone NOT NULL,
    CONSTRAINT pk_series_entries PRIMARY KEY (id),
    CONSTRAINT fk_series_entries__series
        FOREIGN KEY (series_id)
        REFERENCES publishing.series (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_series_entries__content_items
        FOREIGN KEY (content_item_id)
        REFERENCES publishing.content_items (id)
        ON DELETE RESTRICT,
    CONSTRAINT uq_series_entries__series_content UNIQUE (series_id, content_item_id),
    CONSTRAINT uq_series_entries__series_position UNIQUE (series_id, position),
    CONSTRAINT ck_series_entries__position_positive CHECK (position > 0)
);

CREATE INDEX ix_series_entries_content_item_id
    ON publishing.series_entries (content_item_id);
