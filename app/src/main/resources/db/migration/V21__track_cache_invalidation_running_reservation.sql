ALTER TABLE operations.cache_invalidation_batches
    ADD COLUMN running_since timestamptz NULL;

UPDATE operations.cache_invalidation_batches
SET running_since = CURRENT_TIMESTAMP
WHERE status = 'RUNNING';

ALTER TABLE operations.cache_invalidation_batches
    ADD CONSTRAINT cache_invalidation_batches_running_since_state_check CHECK (
        (status = 'RUNNING' AND running_since IS NOT NULL)
        OR (status <> 'RUNNING' AND running_since IS NULL)
    ),
    ADD CONSTRAINT cache_invalidation_batches_running_since_requested_check CHECK (
        running_since IS NULL OR running_since >= requested_at
    );

CREATE INDEX cache_invalidation_batches_running_since_running_idx
    ON operations.cache_invalidation_batches (running_since)
    WHERE status = 'RUNNING';
