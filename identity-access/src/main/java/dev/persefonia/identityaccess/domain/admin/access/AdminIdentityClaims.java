package dev.persefonia.identityaccess.domain.admin.access;

import java.util.Objects;

import dev.persefonia.identityaccess.domain.admin.DisplayName;
import dev.persefonia.identityaccess.domain.admin.EmailAddress;
import dev.persefonia.identityaccess.domain.admin.OidcSubject;

public record AdminIdentityClaims(
        OidcSubject oidcSubject,
        EmailAddress email,
        DisplayName displayName) {
    public AdminIdentityClaims {
        Objects.requireNonNull(oidcSubject, "oidcSubject");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(displayName, "displayName");
    }

    public static AdminIdentityClaims of(
            OidcSubject oidcSubject,
            EmailAddress email,
            DisplayName displayName) {
        return new AdminIdentityClaims(oidcSubject, email, displayName);
    }
}
