CREATE TABLE taxonomy.tags (
    id uuid NOT NULL,
    name text NOT NULL,
    normalized_name text NOT NULL,
    slug text NOT NULL,
    description text NULL,
    status text NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT pk_tags PRIMARY KEY (id),
    CONSTRAINT uq_tags_normalized_name UNIQUE (normalized_name),
    CONSTRAINT uq_tags_slug UNIQUE (slug),
    CONSTRAINT ck_tags_name_nonblank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_tags_normalized_name_nonblank CHECK (btrim(normalized_name) <> ''),
    CONSTRAINT ck_tags_slug_nonblank CHECK (btrim(slug) <> ''),
    CONSTRAINT ck_tags_slug_format CHECK (slug ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$'),
    CONSTRAINT ck_tags_description_nonblank CHECK (description IS NULL OR btrim(description) <> ''),
    CONSTRAINT ck_tags_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_tags_updated_not_before_created CHECK (updated_at >= created_at),
    CONSTRAINT ck_tags_version_nonnegative CHECK (version >= 0),
    CONSTRAINT ck_tags_name_length CHECK (char_length(name) <= 80),
    CONSTRAINT ck_tags_normalized_name_length CHECK (char_length(normalized_name) <= 80),
    CONSTRAINT ck_tags_slug_length CHECK (char_length(slug) <= 100),
    CONSTRAINT ck_tags_description_length CHECK (description IS NULL OR char_length(description) <= 500)
);

CREATE INDEX ix_tags_status ON taxonomy.tags (status);
CREATE INDEX ix_tags_created_at ON taxonomy.tags (created_at);
CREATE INDEX ix_tags_updated_at ON taxonomy.tags (updated_at);
