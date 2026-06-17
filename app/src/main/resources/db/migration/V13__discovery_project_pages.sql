ALTER TABLE discovery.discoverable_resources
    DROP CONSTRAINT ck_discoverable_resources_source_context,
    DROP CONSTRAINT ck_discoverable_resources_source_type,
    DROP CONSTRAINT ck_discoverable_resources_resource_type;

ALTER TABLE discovery.discoverable_resources
    ADD CONSTRAINT ck_discoverable_resources_source_context
        CHECK (source_context IN ('CONTENT_PUBLISHING', 'TAXONOMY', 'PROFILE_PORTFOLIO')),
    ADD CONSTRAINT ck_discoverable_resources_source_type
        CHECK (source_type IN ('CONTENT_ITEM', 'TAG', 'SERIES', 'PROJECT')),
    ADD CONSTRAINT ck_discoverable_resources_resource_type
        CHECK (resource_type IN ('ARTICLE', 'NOTE', 'RESEARCH', 'PAGE', 'TAG', 'SERIES', 'PROJECT')),
    ADD CONSTRAINT ck_discoverable_resources_project_projection CHECK (
        (
            source_context <> 'PROFILE_PORTFOLIO'
            AND source_type <> 'PROJECT'
            AND resource_type <> 'PROJECT'
        )
        OR (
            source_context = 'PROFILE_PORTFOLIO'
            AND source_type = 'PROJECT'
            AND resource_type = 'PROJECT'
            AND route_purpose = 'DETAIL'
            AND language IN ('TR', 'EN')
            AND indexing_policy = 'NO_INDEX'
            AND search_eligibility = 'NOT_ELIGIBLE'
            AND sitemap_eligibility = 'NOT_ELIGIBLE'
            AND feed_eligibility = 'NOT_ELIGIBLE'
        )
    );
