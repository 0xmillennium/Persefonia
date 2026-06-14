CREATE TABLE discovery.discoverable_resources (
    id uuid NOT NULL,
    source_context text NOT NULL,
    source_type text NOT NULL,
    source_entity_id uuid NOT NULL,
    resource_type text NOT NULL,
    route_purpose text NOT NULL,
    language text NULL,
    public_url text NOT NULL,
    canonical_url text NOT NULL,
    title text NOT NULL,
    summary text NOT NULL,
    indexing_policy text NOT NULL,
    search_eligibility text NOT NULL,
    sitemap_eligibility text NOT NULL,
    feed_eligibility text NOT NULL,
    og_title text NULL,
    og_description text NULL,
    og_image_asset_id uuid NULL,
    published_at timestamp with time zone NULL,
    source_updated_at timestamp with time zone NULL,
    search_text text NOT NULL,
    created_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT pk_discoverable_resources PRIMARY KEY (id),
    CONSTRAINT uq_discoverable_resources_key
        UNIQUE NULLS NOT DISTINCT (
            source_context,
            source_type,
            source_entity_id,
            resource_type,
            language,
            route_purpose
        ),
    CONSTRAINT uq_discoverable_resources_public_url UNIQUE (public_url),
    CONSTRAINT uq_discoverable_resources_canonical_url UNIQUE (canonical_url),
    CONSTRAINT ck_discoverable_resources_source_context_nonblank CHECK (btrim(source_context) <> ''),
    CONSTRAINT ck_discoverable_resources_source_type_nonblank CHECK (btrim(source_type) <> ''),
    CONSTRAINT ck_discoverable_resources_public_url_nonblank CHECK (btrim(public_url) <> ''),
    CONSTRAINT ck_discoverable_resources_public_url_path
        CHECK (left(public_url, 1) = '/' AND left(public_url, 2) <> '//'),
    CONSTRAINT ck_discoverable_resources_canonical_url_nonblank CHECK (btrim(canonical_url) <> ''),
    CONSTRAINT ck_discoverable_resources_title_nonblank CHECK (btrim(title) <> ''),
    CONSTRAINT ck_discoverable_resources_summary_nonblank CHECK (btrim(summary) <> ''),
    CONSTRAINT ck_discoverable_resources_search_text_nonblank CHECK (btrim(search_text) <> ''),
    CONSTRAINT ck_discoverable_resources_version_nonnegative CHECK (version >= 0),
    CONSTRAINT ck_discoverable_resources_source_context CHECK (source_context IN ('CONTENT_PUBLISHING')),
    CONSTRAINT ck_discoverable_resources_source_type CHECK (source_type IN ('CONTENT_ITEM')),
    CONSTRAINT ck_discoverable_resources_resource_type CHECK (resource_type IN ('ARTICLE', 'NOTE', 'RESEARCH', 'PAGE')),
    CONSTRAINT ck_discoverable_resources_route_purpose CHECK (route_purpose IN ('DETAIL')),
    CONSTRAINT ck_discoverable_resources_language CHECK (language IS NULL OR language IN ('TR', 'EN')),
    CONSTRAINT ck_discoverable_resources_indexing_policy CHECK (indexing_policy IN ('INDEX', 'NO_INDEX')),
    CONSTRAINT ck_discoverable_resources_search_eligibility
        CHECK (search_eligibility IN ('ELIGIBLE', 'NOT_ELIGIBLE')),
    CONSTRAINT ck_discoverable_resources_sitemap_eligibility
        CHECK (sitemap_eligibility IN ('ELIGIBLE', 'NOT_ELIGIBLE')),
    CONSTRAINT ck_discoverable_resources_feed_eligibility
        CHECK (feed_eligibility IN ('ELIGIBLE', 'NOT_ELIGIBLE')),
    CONSTRAINT ck_discoverable_resources_og_title_nonblank CHECK (og_title IS NULL OR btrim(og_title) <> ''),
    CONSTRAINT ck_discoverable_resources_og_description_nonblank
        CHECK (og_description IS NULL OR btrim(og_description) <> '')
);

CREATE INDEX ix_discoverable_resources_source_ref
    ON discovery.discoverable_resources (source_context, source_type, source_entity_id);

CREATE TABLE discovery.redirect_rules (
    id uuid NOT NULL,
    source_url text NOT NULL,
    target_url text NOT NULL,
    status_code integer NOT NULL,
    reason text NOT NULL,
    source_context text NULL,
    source_type text NULL,
    source_entity_id uuid NULL,
    active boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT pk_redirect_rules PRIMARY KEY (id),
    CONSTRAINT ck_redirect_rules_source_url_nonblank CHECK (btrim(source_url) <> ''),
    CONSTRAINT ck_redirect_rules_source_url_path CHECK (left(source_url, 1) = '/' AND left(source_url, 2) <> '//'),
    CONSTRAINT ck_redirect_rules_target_url_nonblank CHECK (btrim(target_url) <> ''),
    CONSTRAINT ck_redirect_rules_target_url_path CHECK (left(target_url, 1) = '/' AND left(target_url, 2) <> '//'),
    CONSTRAINT ck_redirect_rules_source_target_different CHECK (source_url <> target_url),
    CONSTRAINT ck_redirect_rules_status_code CHECK (status_code IN (301, 302, 307, 308)),
    CONSTRAINT ck_redirect_rules_reason CHECK (reason IN ('SLUG_CHANGED', 'MANUAL')),
    CONSTRAINT ck_redirect_rules_slug_changed_301 CHECK (reason <> 'SLUG_CHANGED' OR status_code = 301),
    CONSTRAINT ck_redirect_rules_version_nonnegative CHECK (version >= 0),
    CONSTRAINT ck_redirect_rules_source_ref_all_or_none CHECK (
        (source_context IS NULL AND source_type IS NULL AND source_entity_id IS NULL)
        OR
        (source_context IS NOT NULL AND source_type IS NOT NULL AND source_entity_id IS NOT NULL)
    ),
    CONSTRAINT ck_redirect_rules_source_context
        CHECK (source_context IS NULL OR source_context IN ('CONTENT_PUBLISHING')),
    CONSTRAINT ck_redirect_rules_source_type CHECK (source_type IS NULL OR source_type IN ('CONTENT_ITEM'))
);

CREATE UNIQUE INDEX uq_redirect_rules_active_source_url
    ON discovery.redirect_rules (source_url)
    WHERE active = true;

CREATE INDEX ix_redirect_rules_source_ref
    ON discovery.redirect_rules (source_context, source_type, source_entity_id)
    WHERE source_context IS NOT NULL;
