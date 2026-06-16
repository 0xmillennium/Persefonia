ALTER TABLE discovery.discoverable_resources
    DROP CONSTRAINT ck_discoverable_resources_source_type,
    DROP CONSTRAINT ck_discoverable_resources_resource_type,
    DROP CONSTRAINT ck_discoverable_resources_route_purpose;

ALTER TABLE discovery.discoverable_resources
    ADD CONSTRAINT ck_discoverable_resources_source_type
        CHECK (source_type IN ('CONTENT_ITEM', 'TAG', 'SERIES')),
    ADD CONSTRAINT ck_discoverable_resources_resource_type
        CHECK (resource_type IN ('ARTICLE', 'NOTE', 'RESEARCH', 'PAGE', 'TAG', 'SERIES')),
    ADD CONSTRAINT ck_discoverable_resources_route_purpose
        CHECK (route_purpose IN ('DETAIL', 'TAG_PAGE', 'SERIES_PAGE')),
    ADD CONSTRAINT ck_discoverable_resources_series_projection CHECK (
        (
            source_type <> 'SERIES'
            AND resource_type <> 'SERIES'
            AND route_purpose <> 'SERIES_PAGE'
        )
        OR (
            source_context = 'CONTENT_PUBLISHING'
            AND source_type = 'SERIES'
            AND resource_type = 'SERIES'
            AND route_purpose = 'SERIES_PAGE'
            AND language IN ('TR', 'EN')
            AND indexing_policy = 'NO_INDEX'
            AND search_eligibility = 'NOT_ELIGIBLE'
            AND sitemap_eligibility = 'NOT_ELIGIBLE'
            AND feed_eligibility = 'NOT_ELIGIBLE'
        )
    );
