CREATE TABLE communication.contact_messages (
    id uuid PRIMARY KEY,
    sender_name text NOT NULL,
    sender_email text NOT NULL,
    subject text NOT NULL,
    body text NOT NULL,
    status text NOT NULL,
    mail_delivery_status text NOT NULL,
    submitted_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL,

    CONSTRAINT contact_messages_sender_name_not_blank
        CHECK (btrim(sender_name) <> ''),
    CONSTRAINT contact_messages_sender_email_not_blank
        CHECK (btrim(sender_email) <> ''),
    CONSTRAINT contact_messages_subject_not_blank
        CHECK (btrim(subject) <> ''),
    CONSTRAINT contact_messages_body_not_blank
        CHECK (btrim(body) <> ''),
    CONSTRAINT contact_messages_status_check
        CHECK (status IN ('NEW', 'READ', 'REPLIED', 'SPAM', 'ARCHIVED')),
    CONSTRAINT contact_messages_mail_delivery_status_check
        CHECK (mail_delivery_status IN ('NOT_ATTEMPTED', 'SENT', 'FAILED')),
    CONSTRAINT contact_messages_version_non_negative
        CHECK (version >= 0)
);

CREATE INDEX contact_messages_status_submitted_at_idx
    ON communication.contact_messages (status, submitted_at DESC);

CREATE INDEX contact_messages_submitted_at_idx
    ON communication.contact_messages (submitted_at DESC);

CREATE INDEX contact_messages_mail_delivery_status_idx
    ON communication.contact_messages (mail_delivery_status);

CREATE TABLE communication.mail_notification_attempts (
    id uuid PRIMARY KEY,
    contact_message_id uuid NOT NULL,
    result text NOT NULL,
    attempted_at timestamptz NOT NULL,
    failure_reason text NULL,

    CONSTRAINT mail_notification_attempts_message_fk
        FOREIGN KEY (contact_message_id)
        REFERENCES communication.contact_messages(id)
        ON DELETE CASCADE,
    CONSTRAINT mail_notification_attempts_result_check
        CHECK (result IN ('SENT', 'FAILED')),
    CONSTRAINT mail_notification_attempts_failure_reason_not_blank
        CHECK (failure_reason IS NULL OR btrim(failure_reason) <> '')
);

CREATE INDEX mail_notification_attempts_message_attempted_at_idx
    ON communication.mail_notification_attempts (contact_message_id, attempted_at DESC);

CREATE TABLE communication.contact_message_status_changes (
    id uuid PRIMARY KEY,
    contact_message_id uuid NOT NULL,
    previous_status text NOT NULL,
    new_status text NOT NULL,
    changed_by_admin_id uuid NOT NULL,
    changed_at timestamptz NOT NULL,

    CONSTRAINT contact_status_changes_message_fk
        FOREIGN KEY (contact_message_id)
        REFERENCES communication.contact_messages(id)
        ON DELETE CASCADE,
    CONSTRAINT contact_status_changes_previous_status_check
        CHECK (previous_status IN ('NEW', 'READ', 'REPLIED', 'SPAM', 'ARCHIVED')),
    CONSTRAINT contact_status_changes_new_status_check
        CHECK (new_status IN ('NEW', 'READ', 'REPLIED', 'SPAM', 'ARCHIVED')),
    CONSTRAINT contact_status_changes_status_changed
        CHECK (previous_status <> new_status)
);

CREATE INDEX contact_status_changes_message_changed_at_idx
    ON communication.contact_message_status_changes (contact_message_id, changed_at DESC);

-- Insights stores only privacy-safe aggregate counters. A dimension is a bounded
-- (metric, surface) pair; it never carries paths, search terms, identifiers, or
-- any visitor metadata. Counters accumulate daily totals per dimension.
CREATE TABLE insights.analytics_dimensions (
    id uuid PRIMARY KEY,
    metric text NOT NULL,
    surface text NOT NULL,
    created_at timestamptz NOT NULL,

    CONSTRAINT analytics_dimensions_metric_check
        CHECK (metric IN (
            'PUBLIC_PAGE_VIEW',
            'PUBLIC_SEARCH_SUBMITTED',
            'PUBLIC_CV_VIEWED',
            'PUBLIC_CV_DOWNLOADED',
            'PUBLIC_CONTACT_SUBMITTED',
            'PUBLIC_NOT_FOUND'
        )),
    CONSTRAINT analytics_dimensions_surface_check
        CHECK (surface IN (
            'HOME',
            'CONTENT_DETAIL',
            'PROJECT_INDEX',
            'PROJECT_DETAIL',
            'TAG_INDEX',
            'SERIES_INDEX',
            'CONTACT',
            'CV',
            'SEARCH',
            'NOT_FOUND'
        ))
);

CREATE UNIQUE INDEX analytics_dimensions_unique_idx
    ON insights.analytics_dimensions (
        metric,
        surface
    );

CREATE TABLE insights.analytics_counters (
    id uuid PRIMARY KEY,
    metric text NOT NULL,
    period_start date NOT NULL,
    period_granularity text NOT NULL,
    dimension_id uuid NOT NULL,
    count bigint NOT NULL,
    first_seen_at timestamptz NOT NULL,
    last_seen_at timestamptz NOT NULL,
    version bigint NOT NULL,

    CONSTRAINT analytics_counters_dimension_fk
        FOREIGN KEY (dimension_id)
        REFERENCES insights.analytics_dimensions(id)
        ON DELETE CASCADE,
    CONSTRAINT analytics_counters_metric_check
        CHECK (metric IN (
            'PUBLIC_PAGE_VIEW',
            'PUBLIC_SEARCH_SUBMITTED',
            'PUBLIC_CV_VIEWED',
            'PUBLIC_CV_DOWNLOADED',
            'PUBLIC_CONTACT_SUBMITTED',
            'PUBLIC_NOT_FOUND'
        )),
    CONSTRAINT analytics_counters_period_granularity_check
        CHECK (period_granularity IN ('DAY')),
    CONSTRAINT analytics_counters_count_non_negative
        CHECK (count >= 0),
    CONSTRAINT analytics_counters_version_non_negative
        CHECK (version >= 0),
    CONSTRAINT analytics_counters_seen_order
        CHECK (last_seen_at >= first_seen_at)
);

CREATE UNIQUE INDEX analytics_counters_unique_idx
    ON insights.analytics_counters (
        metric,
        period_start,
        period_granularity,
        dimension_id
    );
