package dev.persefonia.identityaccess.application.admin.authorization;

import java.util.Objects;
import java.util.Set;

import dev.persefonia.identityaccess.domain.admin.AdminAccountId;
import dev.persefonia.identityaccess.domain.admin.AdminAccountStatus;
import dev.persefonia.identityaccess.domain.admin.AdminRole;

public record AdminCommandActor(
        AdminAccountId accountId,
        AdminAccountStatus status,
        Set<AdminRole> roles) {
    public AdminCommandActor {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(status, "status");
        roles = Set.copyOf(Objects.requireNonNull(roles, "roles"));
        if (status == AdminAccountStatus.ACTIVE && roles.isEmpty()) {
            throw new IllegalArgumentException("active command actor must have at least one role");
        }
    }

    public boolean isActive() {
        return status == AdminAccountStatus.ACTIVE;
    }

    public boolean hasRole(AdminRole role) {
        return roles.contains(Objects.requireNonNull(role, "role"));
    }

    public boolean isOwner() {
        return hasRole(AdminRole.OWNER);
    }

    public boolean isEditor() {
        return hasRole(AdminRole.EDITOR);
    }
}
