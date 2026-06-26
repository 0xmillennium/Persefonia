-- Append-only, privacy-safe audit foundation. An audit record captures who did
-- what to which entity, with ordered safe field changes and ordered safe
-- metadata. Records never store private operational data, raw identity data, or full
-- content text, and they hold no physical cross-context foreign keys.
CREATE TABLE audit.audit_records (
    id uuid NOT NULL,
    action text NOT NULL,
    actor_type text NOT NULL,
    actor_context text NULL,
    actor_source_type text NULL,
    actor_id uuid NULL,
    actor_display text NOT NULL,
    entity_context text NOT NULL,
    entity_type text NOT NULL,
    entity_id uuid NOT NULL,
    request_id text NULL,
    occurred_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,

    CONSTRAINT audit_records_pk PRIMARY KEY (id),
    CONSTRAINT audit_records_action_not_blank
        CHECK (btrim(action) <> ''),
    CONSTRAINT audit_records_actor_type_check
        CHECK (actor_type IN ('ADMIN', 'SYSTEM')),
    CONSTRAINT audit_records_actor_display_not_blank
        CHECK (btrim(actor_display) <> ''),
    CONSTRAINT audit_records_entity_context_not_blank
        CHECK (btrim(entity_context) <> ''),
    CONSTRAINT audit_records_entity_type_not_blank
        CHECK (btrim(entity_type) <> ''),
    CONSTRAINT audit_records_request_id_not_blank
        CHECK (request_id IS NULL OR btrim(request_id) <> ''),
    CONSTRAINT audit_records_admin_actor_requires_reference
        CHECK (actor_type <> 'ADMIN'
            OR (actor_context IS NOT NULL
                AND actor_source_type IS NOT NULL
                AND actor_id IS NOT NULL)),
    CONSTRAINT audit_records_system_actor_has_no_reference
        CHECK (actor_type <> 'SYSTEM'
            OR (actor_context IS NULL
                AND actor_source_type IS NULL
                AND actor_id IS NULL))
);

CREATE INDEX audit_records_occurred_at_idx
    ON audit.audit_records (occurred_at DESC);

CREATE INDEX audit_records_entity_occurred_at_idx
    ON audit.audit_records (entity_context, entity_type, entity_id, occurred_at DESC);

CREATE INDEX audit_records_actor_occurred_at_idx
    ON audit.audit_records (actor_type, actor_id, occurred_at DESC);

CREATE INDEX audit_records_action_occurred_at_idx
    ON audit.audit_records (action, occurred_at DESC);

CREATE INDEX audit_records_request_id_idx
    ON audit.audit_records (request_id)
    WHERE request_id IS NOT NULL;

CREATE TABLE audit.audit_record_changes (
    id uuid NOT NULL,
    audit_record_id uuid NOT NULL,
    field_path text NOT NULL,
    old_value text NULL,
    new_value text NULL,
    position integer NOT NULL,

    CONSTRAINT audit_record_changes_pk PRIMARY KEY (id),
    CONSTRAINT audit_record_changes_record_fk
        FOREIGN KEY (audit_record_id)
        REFERENCES audit.audit_records(id),
    CONSTRAINT audit_record_changes_field_path_not_blank
        CHECK (btrim(field_path) <> ''),
    CONSTRAINT audit_record_changes_old_value_not_blank
        CHECK (old_value IS NULL OR btrim(old_value) <> ''),
    CONSTRAINT audit_record_changes_new_value_not_blank
        CHECK (new_value IS NULL OR btrim(new_value) <> ''),
    CONSTRAINT audit_record_changes_value_present
        CHECK (old_value IS NOT NULL OR new_value IS NOT NULL),
    CONSTRAINT audit_record_changes_position_non_negative
        CHECK (position >= 0),
    CONSTRAINT audit_record_changes_unique_position
        UNIQUE (audit_record_id, position),
    CONSTRAINT audit_record_changes_unique_field_path
        UNIQUE (audit_record_id, field_path)
);

CREATE INDEX audit_record_changes_record_position_idx
    ON audit.audit_record_changes (audit_record_id, position);

CREATE TABLE audit.audit_record_metadata (
    id uuid NOT NULL,
    audit_record_id uuid NOT NULL,
    metadata_key text NOT NULL,
    metadata_value text NOT NULL,
    position integer NOT NULL,

    CONSTRAINT audit_record_metadata_pk PRIMARY KEY (id),
    CONSTRAINT audit_record_metadata_record_fk
        FOREIGN KEY (audit_record_id)
        REFERENCES audit.audit_records(id),
    CONSTRAINT audit_record_metadata_key_not_blank
        CHECK (btrim(metadata_key) <> ''),
    CONSTRAINT audit_record_metadata_value_not_blank
        CHECK (btrim(metadata_value) <> ''),
    CONSTRAINT audit_record_metadata_position_non_negative
        CHECK (position >= 0),
    CONSTRAINT audit_record_metadata_unique_key
        UNIQUE (audit_record_id, metadata_key),
    CONSTRAINT audit_record_metadata_unique_position
        UNIQUE (audit_record_id, position)
);

CREATE INDEX audit_record_metadata_record_position_idx
    ON audit.audit_record_metadata (audit_record_id, position);
