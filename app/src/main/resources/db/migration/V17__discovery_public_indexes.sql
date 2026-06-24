ALTER TABLE discovery.discoverable_resources
    DROP CONSTRAINT ck_discoverable_resources_project_projection;

ALTER TABLE discovery.discoverable_resources
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
            AND feed_eligibility = 'NOT_ELIGIBLE'
            AND (
                (
                    indexing_policy = 'NO_INDEX'
                    AND search_eligibility = 'NOT_ELIGIBLE'
                    AND sitemap_eligibility = 'NOT_ELIGIBLE'
                )
                OR (
                    indexing_policy = 'INDEX'
                    AND search_eligibility = 'ELIGIBLE'
                    AND sitemap_eligibility = 'ELIGIBLE'
                )
            )
        )
    );

CREATE INDEX ix_discoverable_resources_search_fts
ON discovery.discoverable_resources
USING GIN (to_tsvector('simple', coalesce(search_text, '')))
WHERE indexing_policy = 'INDEX'
  AND search_eligibility = 'ELIGIBLE';

CREATE INDEX ix_discoverable_resources_public_sitemap
ON discovery.discoverable_resources (language, source_updated_at DESC, public_url)
WHERE indexing_policy = 'INDEX'
  AND sitemap_eligibility = 'ELIGIBLE';

CREATE INDEX ix_discoverable_resources_public_feed
ON discovery.discoverable_resources (published_at DESC, source_updated_at DESC, public_url)
WHERE indexing_policy = 'INDEX'
  AND feed_eligibility = 'ELIGIBLE';
