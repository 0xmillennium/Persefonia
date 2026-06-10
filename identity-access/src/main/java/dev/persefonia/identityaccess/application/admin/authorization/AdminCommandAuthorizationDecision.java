package dev.persefonia.identityaccess.application.admin.authorization;

import java.util.Objects;
import java.util.Optional;

public final class AdminCommandAuthorizationDecision {
    private static final AdminCommandAuthorizationDecision ALLOWED =
            new AdminCommandAuthorizationDecision(null);

    private final AdminCommandAuthorizationDenialReason denialReason;

    private AdminCommandAuthorizationDecision(AdminCommandAuthorizationDenialReason denialReason) {
        this.denialReason = denialReason;
    }

    public static AdminCommandAuthorizationDecision allowed() {
        return ALLOWED;
    }

    public static AdminCommandAuthorizationDecision denied(AdminCommandAuthorizationDenialReason reason) {
        return new AdminCommandAuthorizationDecision(Objects.requireNonNull(reason, "reason"));
    }

    public boolean isAllowed() {
        return denialReason == null;
    }

    public Optional<AdminCommandAuthorizationDenialReason> denialReason() {
        return Optional.ofNullable(denialReason);
    }

    public void throwIfDenied(AdminCommand command) {
        Objects.requireNonNull(command, "command");
        if (!isAllowed()) {
            throw new AdminCommandAuthorizationException(denialReason, command);
        }
    }
}
