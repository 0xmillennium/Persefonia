package dev.persefonia.identityaccess.domain.admin.access;

public enum AdminAccessDenialReason {
    NOT_ALLOWLISTED,
    INITIAL_OWNER_BOOTSTRAP_DISABLED,
    AUTOMATIC_PROVISIONING_DISABLED,
    EMAIL_ALREADY_BOUND,
    DISABLED_ACCOUNT
}
