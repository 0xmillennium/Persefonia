CREATE INDEX ix_discoverable_resources_homepage_content
ON discovery.discoverable_resources
    (language, resource_type, published_at DESC, source_updated_at DESC NULLS LAST, public_url)
WHERE source_context = 'CONTENT_PUBLISHING'
  AND source_type = 'CONTENT_ITEM'
  AND route_purpose = 'DETAIL'
  AND indexing_policy = 'INDEX'
  AND resource_type IN ('ARTICLE', 'NOTE', 'RESEARCH')
  AND published_at IS NOT NULL
  AND public_url IS NOT NULL;
