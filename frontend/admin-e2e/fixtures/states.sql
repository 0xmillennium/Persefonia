-- Apply only to the guarded preview's disposable persefonia_browser database,
-- after frontend/e2e/fixtures/content.sql. No provider or mail action is executed.
INSERT INTO publishing.series (id, language, slug, title, description, status, created_at, updated_at, version)
VALUES ('20000000-0000-0000-0000-000000000001', 'EN', 'synthetic-browser-series',
        'Synthetic browser series', 'Disposable editor state.', 'ACTIVE', now(), now(), 0)
ON CONFLICT (id) DO NOTHING;
INSERT INTO communication.contact_messages
    (id, sender_name, sender_email, subject, body, status, mail_delivery_status, submitted_at, updated_at, version)
VALUES ('20000000-0000-0000-0000-000000000002', 'Synthetic sender', 'preview@example.invalid',
        'Synthetic local inbox state', 'Test-owned message. No email is sent.', 'NEW', 'NOT_ATTEMPTED', now(), now(), 0)
ON CONFLICT (id) DO NOTHING;
INSERT INTO operations.cache_invalidation_batches
    (id, reason, requested_by, requested_at, status, completed_at, version)
VALUES ('20000000-0000-0000-0000-000000000003', 'PUBLIC_RESOURCE_CHANGED', 'SYSTEM',
        now(), 'COMPLETED', now(), 0)
ON CONFLICT (id) DO NOTHING;
INSERT INTO operations.cache_invalidation_targets
    (id, batch_id, target_type, target_value, status)
VALUES ('20000000-0000-0000-0000-000000000004', '20000000-0000-0000-0000-000000000003',
        'CACHE_TAG', 'synthetic-preview', 'PURGED')
ON CONFLICT (id) DO NOTHING;
INSERT INTO operations.cache_purge_attempts
    (id, batch_id, attempt_number, provider, attempted_at, result)
SELECT '20000000-0000-0000-0000-000000000005', id, 1, 'LOCAL', completed_at, 'SUCCESS'
FROM operations.cache_invalidation_batches WHERE id = '20000000-0000-0000-0000-000000000003'
ON CONFLICT (id) DO NOTHING;
