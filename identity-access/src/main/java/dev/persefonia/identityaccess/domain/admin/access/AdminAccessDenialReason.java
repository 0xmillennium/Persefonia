package dev.persefonia.identityaccess.domain.admin.access;

public enum AdminAccessDenialReason {
    REQUIRED_OIDC_GROUP_MISSING,
    ADMIN_ACCOUNT_NOT_FOUND,
    INITIAL_OWNER_BOOTSTRAP_DISABLED,
    AUTOMATIC_PROVISIONING_DISABLED,
    EMAIL_ALREADY_BOUND,
    DISABLED_ACCOUNT
}
