CREATE TABLE publishing.translation_groups (
    id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT pk_translation_groups PRIMARY KEY (id),
    CONSTRAINT ck_translation_groups__updated_not_before_created CHECK (updated_at >= created_at),
    CONSTRAINT ck_translation_groups__version_non_negative CHECK (version >= 0)
);

CREATE TABLE publishing.translation_group_entries (
    id uuid NOT NULL,
    translation_group_id uuid NOT NULL,
    content_item_id uuid NOT NULL,
    language text NOT NULL,
    content_type text NOT NULL,
    added_at timestamp with time zone NOT NULL,
    CONSTRAINT pk_translation_group_entries PRIMARY KEY (id),
    CONSTRAINT fk_translation_group_entries__translation_groups
        FOREIGN KEY (translation_group_id)
        REFERENCES publishing.translation_groups (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_translation_group_entries__content_items
        FOREIGN KEY (content_item_id)
        REFERENCES publishing.content_items (id)
        ON DELETE RESTRICT,
    CONSTRAINT uq_translation_group_entries__content_item UNIQUE (content_item_id),
    CONSTRAINT uq_translation_group_entries__group_language UNIQUE (translation_group_id, language),
    CONSTRAINT ck_translation_group_entries__language CHECK (language IN ('TR', 'EN')),
    CONSTRAINT ck_translation_group_entries__content_type CHECK (
        content_type IN ('ARTICLE', 'NOTE', 'RESEARCH', 'PAGE')
    )
);

CREATE INDEX ix_translation_group_entries_group_id
    ON publishing.translation_group_entries (translation_group_id);

CREATE INDEX ix_translation_group_entries_content_item_id
    ON publishing.translation_group_entries (content_item_id);

CREATE INDEX ix_translation_group_entries_language
    ON publishing.translation_group_entries (language);
