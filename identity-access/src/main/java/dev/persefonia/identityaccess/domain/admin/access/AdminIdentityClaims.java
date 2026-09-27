package dev.persefonia.identityaccess.domain.admin.access;

import java.util.Objects;
import java.util.Set;

import dev.persefonia.identityaccess.domain.admin.DisplayName;
import dev.persefonia.identityaccess.domain.admin.EmailAddress;
import dev.persefonia.identityaccess.domain.admin.OidcGroup;
import dev.persefonia.identityaccess.domain.admin.OidcSubject;

public record AdminIdentityClaims(
        OidcSubject oidcSubject,
        EmailAddress email,
        DisplayName displayName,
        Set<OidcGroup> oidcGroups) {
    public AdminIdentityClaims {
        Objects.requireNonNull(oidcSubject, "oidcSubject");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(displayName, "displayName");
        oidcGroups = Set.copyOf(Objects.requireNonNull(oidcGroups, "oidcGroups"));
    }

    public static AdminIdentityClaims of(
            OidcSubject oidcSubject,
            EmailAddress email,
            DisplayName displayName,
            Set<OidcGroup> oidcGroups) {
        return new AdminIdentityClaims(oidcSubject, email, displayName, oidcGroups);
    }
}
