CREATE TABLE media.assets (
    id uuid NOT NULL,
    original_filename text NOT NULL,
    stored_filename text NOT NULL,
    storage_path text NOT NULL,
    public_url text NULL,
    content_type text NOT NULL,
    file_extension text NOT NULL,
    size_bytes bigint NOT NULL,
    checksum text NOT NULL,
    kind text NOT NULL,
    visibility text NOT NULL,
    image_width integer NULL,
    image_height integer NULL,
    alt_text text NULL,
    decorative boolean NOT NULL DEFAULT false,
    processing_status text NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT pk_assets PRIMARY KEY (id),
    CONSTRAINT uq_assets__checksum UNIQUE (checksum),
    CONSTRAINT uq_assets__storage_path UNIQUE (storage_path),
    CONSTRAINT ck_assets__original_filename_not_blank CHECK (btrim(original_filename) <> ''),
    CONSTRAINT ck_assets__stored_filename_not_blank CHECK (btrim(stored_filename) <> ''),
    CONSTRAINT ck_assets__storage_path_not_blank CHECK (btrim(storage_path) <> ''),
    CONSTRAINT ck_assets__content_type_not_blank CHECK (btrim(content_type) <> ''),
    CONSTRAINT ck_assets__file_extension_not_blank CHECK (btrim(file_extension) <> ''),
    CONSTRAINT ck_assets__size_positive CHECK (size_bytes > 0),
    CONSTRAINT ck_assets__kind CHECK (kind IN ('IMAGE', 'PDF', 'DOCUMENT')),
    CONSTRAINT ck_assets__visibility CHECK (visibility IN ('PUBLIC', 'PRIVATE')),
    CONSTRAINT ck_assets__processing_status CHECK (
        processing_status IN ('PENDING', 'PROCESSED', 'FAILED', 'NOT_REQUIRED')
    ),
    CONSTRAINT ck_assets__image_width_positive CHECK (image_width IS NULL OR image_width > 0),
    CONSTRAINT ck_assets__image_height_positive CHECK (image_height IS NULL OR image_height > 0),
    CONSTRAINT ck_assets__image_dimensions_paired CHECK (
        (image_width IS NULL) = (image_height IS NULL)
    ),
    CONSTRAINT ck_assets__version_non_negative CHECK (version >= 0),
    CONSTRAINT ck_assets__updated_not_before_created CHECK (created_at <= updated_at),
    CONSTRAINT ck_assets__public_image_accessible CHECK (
        kind <> 'IMAGE'
        OR visibility <> 'PUBLIC'
        OR decorative = true
        OR (alt_text IS NOT NULL AND btrim(alt_text) <> '')
    ),
    CONSTRAINT ck_assets__public_image_processed CHECK (
        NOT (
            kind = 'IMAGE'
            AND visibility = 'PUBLIC'
            AND processing_status <> 'PROCESSED'
        )
    ),
    CONSTRAINT ck_assets__image_dimensions CHECK (
        kind <> 'IMAGE'
        OR processing_status = 'PENDING'
        OR (image_width IS NOT NULL AND image_height IS NOT NULL)
    )
);

CREATE INDEX ix_assets__kind ON media.assets (kind);
CREATE INDEX ix_assets__visibility ON media.assets (visibility);
CREATE INDEX ix_assets__processing_status ON media.assets (processing_status);

CREATE TABLE media.asset_variants (
    id uuid NOT NULL,
    asset_id uuid NOT NULL,
    name text NOT NULL,
    width integer NOT NULL,
    height integer NOT NULL,
    content_type text NOT NULL,
    size_bytes bigint NOT NULL,
    storage_path text NOT NULL,
    public_url text NULL,
    checksum text NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT pk_asset_variants PRIMARY KEY (id),
    CONSTRAINT fk_asset_variants__asset
        FOREIGN KEY (asset_id)
        REFERENCES media.assets (id)
        ON DELETE CASCADE,
    CONSTRAINT uq_asset_variants__asset_name UNIQUE (asset_id, name),
    CONSTRAINT uq_asset_variants__storage_path UNIQUE (storage_path),
    CONSTRAINT ck_asset_variants__name CHECK (name IN ('thumbnail', 'medium', 'large', 'og')),
    CONSTRAINT ck_asset_variants__width_positive CHECK (width > 0),
    CONSTRAINT ck_asset_variants__height_positive CHECK (height > 0),
    CONSTRAINT ck_asset_variants__content_type_not_blank CHECK (btrim(content_type) <> ''),
    CONSTRAINT ck_asset_variants__size_positive CHECK (size_bytes > 0),
    CONSTRAINT ck_asset_variants__storage_path_not_blank CHECK (btrim(storage_path) <> ''),
    CONSTRAINT ck_asset_variants__checksum_not_blank CHECK (btrim(checksum) <> '')
);

CREATE INDEX ix_asset_variants__asset_id ON media.asset_variants (asset_id);

CREATE TABLE media.asset_validation_results (
    id uuid NOT NULL,
    asset_id uuid NOT NULL,
    rule text NOT NULL,
    status text NOT NULL,
    message text NULL,
    checked_at timestamp with time zone NOT NULL,
    CONSTRAINT pk_asset_validation_results PRIMARY KEY (id),
    CONSTRAINT fk_asset_validation_results__asset
        FOREIGN KEY (asset_id)
        REFERENCES media.assets (id)
        ON DELETE CASCADE,
    CONSTRAINT uq_asset_validation_results__asset_rule UNIQUE (asset_id, rule),
    CONSTRAINT ck_asset_validation_results__rule_not_blank CHECK (btrim(rule) <> ''),
    CONSTRAINT ck_asset_validation_results__status CHECK (status IN ('PASSED', 'FAILED', 'WARNING'))
);

CREATE INDEX ix_asset_validation_results__asset_id ON media.asset_validation_results (asset_id);
CREATE INDEX ix_asset_validation_results__status ON media.asset_validation_results (status);
