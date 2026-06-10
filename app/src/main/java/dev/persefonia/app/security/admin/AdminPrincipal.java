package dev.persefonia.app.security.admin;

import java.util.Objects;
import java.util.Set;

import dev.persefonia.identityaccess.domain.admin.AdminAccountId;
import dev.persefonia.identityaccess.domain.admin.AdminAccountStatus;
import dev.persefonia.identityaccess.domain.admin.AdminRole;
import dev.persefonia.identityaccess.domain.admin.DisplayName;
import dev.persefonia.identityaccess.domain.admin.EmailAddress;
import dev.persefonia.identityaccess.domain.admin.NormalizedEmailAddress;
import dev.persefonia.identityaccess.domain.admin.OidcSubject;

public record AdminPrincipal(
        AdminAccountId accountId,
        OidcSubject oidcSubject,
        EmailAddress email,
        NormalizedEmailAddress normalizedEmail,
        DisplayName displayName,
        Set<AdminRole> roles,
        AdminAccountStatus status) {
    public AdminPrincipal {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(oidcSubject, "oidcSubject");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(normalizedEmail, "normalizedEmail");
        Objects.requireNonNull(displayName, "displayName");
        roles = Set.copyOf(Objects.requireNonNull(roles, "roles"));
        Objects.requireNonNull(status, "status");

        if (status != AdminAccountStatus.ACTIVE) {
            throw new IllegalArgumentException("admin principal must be active");
        }
        if (roles.isEmpty()) {
            throw new IllegalArgumentException("active admin principal must have at least one role");
        }
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
