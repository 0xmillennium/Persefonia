package dev.persefonia.identityaccess.domain.admin.access;

import java.util.Objects;

public final class AdminAccessDeniedException extends RuntimeException {
    private final AdminAccessDenialReason reason;

    public AdminAccessDeniedException(AdminAccessDenialReason reason) {
        super("Admin access denied: " + Objects.requireNonNull(reason, "reason").name());
        this.reason = reason;
    }

    public AdminAccessDenialReason reason() {
        return reason;
    }
}
