package dev.persefonia.identityaccess.domain.admin.access;

import java.util.Objects;
import java.util.Optional;

public final class AdminAccessDecision {
    private static final AdminAccessDecision ALLOWED = new AdminAccessDecision(null);

    private final AdminAccessDenialReason denialReason;

    private AdminAccessDecision(AdminAccessDenialReason denialReason) {
        this.denialReason = denialReason;
    }

    public static AdminAccessDecision allowed() {
        return ALLOWED;
    }

    public static AdminAccessDecision denied(AdminAccessDenialReason reason) {
        return new AdminAccessDecision(Objects.requireNonNull(reason, "reason"));
    }

    public boolean isAllowed() {
        return denialReason == null;
    }

    public Optional<AdminAccessDenialReason> denialReason() {
        return Optional.ofNullable(denialReason);
    }

    public void throwIfDenied() {
        if (denialReason != null) {
            throw new AdminAccessDeniedException(denialReason);
        }
    }
}
