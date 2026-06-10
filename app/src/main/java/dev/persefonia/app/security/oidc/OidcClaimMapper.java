package dev.persefonia.app.security.oidc;

import java.util.Objects;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

import dev.persefonia.identityaccess.domain.admin.DisplayName;
import dev.persefonia.identityaccess.domain.admin.EmailAddress;
import dev.persefonia.identityaccess.domain.admin.OidcSubject;
import dev.persefonia.identityaccess.domain.admin.access.AdminIdentityClaims;

@Component
public final class OidcClaimMapper {
    public AdminIdentityClaims toAdminIdentityClaims(OidcUser oidcUser) {
        Objects.requireNonNull(oidcUser, "oidcUser");

        String subject = oidcUser.getSubject();
        if (subject == null || subject.isBlank()) {
            throw authenticationFailure("persefonia_oidc_missing_subject", "Required OIDC subject is missing");
        }

        String rawEmail = oidcUser.getClaimAsString("email");
        if (rawEmail == null || rawEmail.isBlank()) {
            throw authenticationFailure("persefonia_oidc_missing_email", "Required OIDC email is missing");
        }

        if (Boolean.FALSE.equals(oidcUser.getClaim("email_verified"))) {
            throw authenticationFailure("persefonia_oidc_unverified_email", "OIDC email is not verified");
        }

        try {
            OidcSubject oidcSubject = OidcSubject.of(subject);
            EmailAddress email = EmailAddress.of(rawEmail);
            return AdminIdentityClaims.of(oidcSubject, email, DisplayName.of(displayName(oidcUser, email)));
        } catch (IllegalArgumentException exception) {
            throw authenticationFailure("persefonia_oidc_invalid_claims", "OIDC claims are invalid", exception);
        }
    }

    private static String displayName(OidcUser oidcUser, EmailAddress email) {
        String name = nonBlankClaim(oidcUser, "name");
        if (name != null) {
            return name;
        }
        String preferredUsername = nonBlankClaim(oidcUser, "preferred_username");
        if (preferredUsername != null) {
            return preferredUsername;
        }
        return email.value().substring(0, email.value().indexOf('@'));
    }

    private static String nonBlankClaim(OidcUser oidcUser, String claimName) {
        String value = oidcUser.getClaimAsString(claimName);
        return value == null || value.isBlank() ? null : value;
    }

    private static OAuth2AuthenticationException authenticationFailure(String code, String description) {
        return new OAuth2AuthenticationException(new OAuth2Error(code, description, null));
    }

    private static OAuth2AuthenticationException authenticationFailure(
            String code,
            String description,
            RuntimeException cause) {
        return new OAuth2AuthenticationException(new OAuth2Error(code, description, null), cause);
    }
}
