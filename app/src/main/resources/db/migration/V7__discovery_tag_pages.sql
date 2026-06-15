ALTER TABLE discovery.discoverable_resources
    DROP CONSTRAINT ck_discoverable_resources_source_context,
    DROP CONSTRAINT ck_discoverable_resources_source_type,
    DROP CONSTRAINT ck_discoverable_resources_resource_type,
    DROP CONSTRAINT ck_discoverable_resources_route_purpose;

ALTER TABLE discovery.discoverable_resources
    ADD CONSTRAINT ck_discoverable_resources_source_context
        CHECK (source_context IN ('CONTENT_PUBLISHING', 'TAXONOMY')),
    ADD CONSTRAINT ck_discoverable_resources_source_type
        CHECK (source_type IN ('CONTENT_ITEM', 'TAG')),
    ADD CONSTRAINT ck_discoverable_resources_resource_type
        CHECK (resource_type IN ('ARTICLE', 'NOTE', 'RESEARCH', 'PAGE', 'TAG')),
    ADD CONSTRAINT ck_discoverable_resources_route_purpose
        CHECK (route_purpose IN ('DETAIL', 'TAG_PAGE')),
    ADD CONSTRAINT ck_discoverable_resources_tag_projection CHECK (
        (
            source_context <> 'TAXONOMY'
            AND source_type <> 'TAG'
            AND resource_type <> 'TAG'
            AND route_purpose <> 'TAG_PAGE'
        )
        OR (
            source_context = 'TAXONOMY'
            AND resource_type = 'TAG'
            AND source_type = 'TAG'
            AND route_purpose = 'TAG_PAGE'
            AND language IN ('TR', 'EN')
            AND indexing_policy = 'NO_INDEX'
            AND search_eligibility = 'NOT_ELIGIBLE'
            AND sitemap_eligibility = 'NOT_ELIGIBLE'
            AND feed_eligibility = 'NOT_ELIGIBLE'
        )
    );
