package dev.persefonia.identityaccess.application.admin.authorization;

import java.util.Objects;

public final class AdminCommandAuthorizationPolicy {
    public AdminCommandAuthorizationDecision evaluateOwnerRequired(
            AdminCommandActor actor,
            AdminCommand command) {
        Objects.requireNonNull(command, "command");
        if (actor == null) {
            return AdminCommandAuthorizationDecision.denied(AdminCommandAuthorizationDenialReason.MISSING_ACTOR);
        }
        if (!actor.isActive()) {
            return AdminCommandAuthorizationDecision.denied(AdminCommandAuthorizationDenialReason.INACTIVE_ADMIN);
        }
        if (!actor.isOwner()) {
            return AdminCommandAuthorizationDecision.denied(AdminCommandAuthorizationDenialReason.OWNER_REQUIRED);
        }
        return AdminCommandAuthorizationDecision.allowed();
    }

    public void requireOwner(AdminCommandActor actor, AdminCommand command) {
        evaluateOwnerRequired(actor, command).throwIfDenied(command);
    }
}
