CREATE TABLE operations.cache_invalidation_batches (
    id uuid NOT NULL,
    reason text NOT NULL,
    requested_by text NOT NULL,
    requested_at timestamptz NOT NULL,
    status text NOT NULL,
    completed_at timestamptz NULL,
    failure_reason text NULL,
    version bigint NOT NULL,

    CONSTRAINT cache_invalidation_batches_pk PRIMARY KEY (id),
    CONSTRAINT cache_invalidation_batches_reason_check
        CHECK (reason IN ('PUBLIC_RESOURCE_CHANGED')),
    CONSTRAINT cache_invalidation_batches_requested_by_check
        CHECK (requested_by IN ('SYSTEM')),
    CONSTRAINT cache_invalidation_batches_status_check
        CHECK (status IN ('REQUESTED', 'RUNNING', 'COMPLETED', 'FAILED', 'PARTIAL')),
    CONSTRAINT cache_invalidation_batches_failure_reason_check
        CHECK (failure_reason IS NULL OR failure_reason IN (
            'NETWORK_ERROR', 'TIMEOUT', 'RATE_LIMITED', 'PROVIDER_5XX',
            'AUTHENTICATION_ERROR', 'INVALID_CONFIGURATION', 'INVALID_TARGET',
            'UNKNOWN_PROVIDER_FAILURE'
        )),
    CONSTRAINT cache_invalidation_batches_state_check CHECK (
        (status IN ('REQUESTED', 'RUNNING') AND failure_reason IS NULL AND completed_at IS NULL)
        OR (status = 'COMPLETED' AND failure_reason IS NULL AND completed_at IS NOT NULL)
        OR (status IN ('FAILED', 'PARTIAL') AND failure_reason IS NOT NULL)
    ),
    CONSTRAINT cache_invalidation_batches_version_check CHECK (version >= 0),
    CONSTRAINT cache_invalidation_batches_completed_at_check
        CHECK (completed_at IS NULL OR completed_at >= requested_at)
);

CREATE INDEX cache_invalidation_batches_status_idx
    ON operations.cache_invalidation_batches (status);

CREATE INDEX cache_invalidation_batches_requested_at_idx
    ON operations.cache_invalidation_batches (requested_at);

CREATE TABLE operations.cache_invalidation_targets (
    id uuid NOT NULL,
    batch_id uuid NOT NULL,
    target_type text NOT NULL,
    target_value text NOT NULL,
    status text NOT NULL,

    CONSTRAINT cache_invalidation_targets_pk PRIMARY KEY (id),
    CONSTRAINT cache_invalidation_targets_batch_fk FOREIGN KEY (batch_id)
        REFERENCES operations.cache_invalidation_batches(id) ON DELETE CASCADE,
    CONSTRAINT cache_invalidation_targets_type_check CHECK (target_type IN ('URL', 'CACHE_TAG')),
    CONSTRAINT cache_invalidation_targets_status_check CHECK (status IN ('PENDING', 'PURGED', 'FAILED', 'SKIPPED')),
    CONSTRAINT cache_invalidation_targets_value_check CHECK (
        btrim(target_value) <> ''
        AND ((target_type = 'URL' AND char_length(target_value) <= 2048)
            OR (target_type = 'CACHE_TAG' AND char_length(target_value) <= 128))
    ),
    CONSTRAINT cache_invalidation_targets_batch_value_unique
        UNIQUE (batch_id, target_type, target_value)
);

CREATE INDEX cache_invalidation_targets_batch_id_idx
    ON operations.cache_invalidation_targets (batch_id);

CREATE INDEX cache_invalidation_targets_status_idx
    ON operations.cache_invalidation_targets (status);

CREATE TABLE operations.cache_purge_attempts (
    id uuid NOT NULL,
    batch_id uuid NOT NULL,
    attempt_number smallint NOT NULL,
    provider text NOT NULL,
    attempted_at timestamptz NOT NULL,
    result text NOT NULL,
    failure_reason text NULL,

    CONSTRAINT cache_purge_attempts_pk PRIMARY KEY (id),
    CONSTRAINT cache_purge_attempts_batch_fk FOREIGN KEY (batch_id)
        REFERENCES operations.cache_invalidation_batches(id) ON DELETE CASCADE,
    CONSTRAINT cache_purge_attempts_number_check CHECK (attempt_number BETWEEN 1 AND 3),
    CONSTRAINT cache_purge_attempts_batch_number_unique UNIQUE (batch_id, attempt_number),
    CONSTRAINT cache_purge_attempts_provider_check CHECK (provider IN ('LOCAL', 'CLOUDFLARE')),
    CONSTRAINT cache_purge_attempts_result_check CHECK (result IN ('SUCCESS', 'FAILED')),
    CONSTRAINT cache_purge_attempts_failure_reason_check CHECK (
        failure_reason IS NULL OR failure_reason IN (
            'NETWORK_ERROR', 'TIMEOUT', 'RATE_LIMITED', 'PROVIDER_5XX',
            'AUTHENTICATION_ERROR', 'INVALID_CONFIGURATION', 'INVALID_TARGET',
            'UNKNOWN_PROVIDER_FAILURE'
        )
    ),
    CONSTRAINT cache_purge_attempts_result_failure_check CHECK (
        (result = 'SUCCESS' AND failure_reason IS NULL)
        OR (result = 'FAILED' AND failure_reason IS NOT NULL)
    )
);

CREATE INDEX cache_purge_attempts_batch_id_idx
    ON operations.cache_purge_attempts (batch_id);

CREATE INDEX cache_purge_attempts_attempted_at_idx
    ON operations.cache_purge_attempts (attempted_at);

CREATE INDEX cache_purge_attempts_result_idx
    ON operations.cache_purge_attempts (result);
