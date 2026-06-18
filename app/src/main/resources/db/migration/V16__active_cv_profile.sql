CREATE TABLE portfolio.active_cv_profiles (
    id uuid NOT NULL,
    singleton_key boolean NOT NULL DEFAULT true,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT pk_active_cv_profiles PRIMARY KEY (id),
    CONSTRAINT uq_active_cv_profiles__singleton_key UNIQUE (singleton_key),
    CONSTRAINT ck_active_cv_profiles__singleton_key_true CHECK (singleton_key = true),
    CONSTRAINT ck_active_cv_profiles__version_non_negative CHECK (version >= 0),
    CONSTRAINT ck_active_cv_profiles__updated_not_before_created CHECK (created_at <= updated_at)
);

CREATE TABLE portfolio.active_cv_documents (
    id uuid NOT NULL,
    active_cv_profile_id uuid NOT NULL,
    language varchar(16) NOT NULL,
    asset_id uuid NOT NULL,
    display_label varchar(160) NULL,
    selected_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT pk_active_cv_documents PRIMARY KEY (id),
    CONSTRAINT fk_active_cv_documents__profile
        FOREIGN KEY (active_cv_profile_id)
        REFERENCES portfolio.active_cv_profiles (id)
        ON DELETE CASCADE,
    CONSTRAINT uq_active_cv_documents__profile_language UNIQUE (active_cv_profile_id, language),
    CONSTRAINT ck_active_cv_documents__language CHECK (language IN ('TR', 'EN')),
    CONSTRAINT ck_active_cv_documents__display_label_not_blank CHECK (
        display_label IS NULL OR btrim(display_label) <> ''
    ),
    CONSTRAINT ck_active_cv_documents__updated_not_before_created CHECK (created_at <= updated_at)
);

CREATE INDEX ix_active_cv_documents__profile_id
    ON portfolio.active_cv_documents (active_cv_profile_id);

INSERT INTO portfolio.active_cv_profiles (
    id,
    singleton_key,
    created_at,
    updated_at,
    version
) VALUES (
    '00000000-0000-0000-0000-000000000801',
    true,
    now(),
    now(),
    0
);
