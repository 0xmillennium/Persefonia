CREATE TABLE iam.admin_accounts (
    id uuid NOT NULL,
    oidc_subject text NOT NULL,
    email text NOT NULL,
    normalized_email text NOT NULL,
    display_name text NOT NULL,
    status text NOT NULL,
    last_login_at timestamp with time zone NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT pk_admin_accounts PRIMARY KEY (id),
    CONSTRAINT uk_admin_accounts_oidc_subject UNIQUE (oidc_subject),
    CONSTRAINT uk_admin_accounts_normalized_email UNIQUE (normalized_email),
    CONSTRAINT chk_admin_accounts_oidc_subject_not_blank CHECK (btrim(oidc_subject) <> ''),
    CONSTRAINT chk_admin_accounts_email_not_blank CHECK (btrim(email) <> ''),
    CONSTRAINT chk_admin_accounts_normalized_email_not_blank CHECK (btrim(normalized_email) <> ''),
    CONSTRAINT chk_admin_accounts_display_name_not_blank CHECK (btrim(display_name) <> ''),
    CONSTRAINT chk_admin_accounts_oidc_subject_max_length CHECK (char_length(oidc_subject) <= 512),
    CONSTRAINT chk_admin_accounts_email_max_length CHECK (char_length(email) <= 320),
    CONSTRAINT chk_admin_accounts_normalized_email_max_length CHECK (char_length(normalized_email) <= 320),
    CONSTRAINT chk_admin_accounts_display_name_max_length CHECK (char_length(display_name) <= 200),
    CONSTRAINT chk_admin_accounts_normalized_email_lowercase CHECK (normalized_email = lower(normalized_email)),
    CONSTRAINT chk_admin_accounts_oidc_subject_trimmed CHECK (oidc_subject = btrim(oidc_subject)),
    CONSTRAINT chk_admin_accounts_email_trimmed CHECK (email = btrim(email)),
    CONSTRAINT chk_admin_accounts_normalized_email_trimmed CHECK (normalized_email = btrim(normalized_email)),
    CONSTRAINT chk_admin_accounts_display_name_trimmed CHECK (display_name = btrim(display_name)),
    CONSTRAINT chk_admin_accounts_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT chk_admin_accounts_updated_at_not_before_created_at CHECK (updated_at >= created_at),
    CONSTRAINT chk_admin_accounts_last_login_at_not_before_created_at CHECK (
        last_login_at IS NULL OR last_login_at >= created_at
    ),
    CONSTRAINT chk_admin_accounts_version_non_negative CHECK (version >= 0)
);

CREATE INDEX idx_admin_accounts_status
    ON iam.admin_accounts (status);

CREATE TABLE iam.admin_account_roles (
    admin_account_id uuid NOT NULL,
    role text NOT NULL,
    CONSTRAINT pk_admin_account_roles PRIMARY KEY (admin_account_id, role),
    CONSTRAINT fk_admin_account_roles_admin_account
        FOREIGN KEY (admin_account_id)
        REFERENCES iam.admin_accounts (id)
        ON DELETE CASCADE,
    CONSTRAINT chk_admin_account_roles_role CHECK (role IN ('OWNER', 'EDITOR'))
);
