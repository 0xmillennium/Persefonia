package dev.persefonia.app.security.oidc;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

import dev.persefonia.identityaccess.domain.admin.DisplayName;
import dev.persefonia.identityaccess.domain.admin.EmailAddress;
import dev.persefonia.identityaccess.domain.admin.OidcGroup;
import dev.persefonia.identityaccess.domain.admin.OidcSubject;
import dev.persefonia.identityaccess.domain.admin.access.AdminIdentityClaims;

@Component
public final class OidcClaimMapper {
    public AdminIdentityClaims toAdminIdentityClaims(OidcUser oidcUser) {
        Objects.requireNonNull(oidcUser, "oidcUser");
        Map<String, Object> claims = new HashMap<>(oidcUser.getIdToken().getClaims());
        if (oidcUser.getUserInfo() != null) {
            claims.putAll(oidcUser.getUserInfo().getClaims());
        }
        return toAdminIdentityClaims(claims);
    }

    public AdminIdentityClaims toAdminIdentityClaims(Map<String, Object> claims) {
        Objects.requireNonNull(claims, "claims");
        String subject = requiredString(claims, "sub", "persefonia_oidc_missing_subject");
        String rawEmail = requiredString(claims, "email", "persefonia_oidc_missing_email");
        if (Boolean.FALSE.equals(claims.get("email_verified"))) {
            throw authenticationFailure("persefonia_oidc_unverified_email", "OIDC email is not verified");
        }
        Set<OidcGroup> groups = groups(claims);
        try {
            EmailAddress email = EmailAddress.of(rawEmail);
            String name = nonBlankString(claims, "name");
            if (name == null) {
                name = nonBlankString(claims, "preferred_username");
            }
            if (name == null) {
                name = email.value().substring(0, email.value().indexOf('@'));
            }
            return AdminIdentityClaims.of(OidcSubject.of(subject), email, DisplayName.of(name), groups);
        } catch (IllegalArgumentException exception) {
            throw authenticationFailure("persefonia_oidc_invalid_claims", "OIDC claims are invalid");
        }
    }

    private static Set<OidcGroup> groups(Map<String, Object> claims) {
        if (!claims.containsKey("groups")) {
            throw authenticationFailure("persefonia_oidc_missing_groups", "Required OIDC groups are missing");
        }
        if (!(claims.get("groups") instanceof Collection<?> values)) {
            throw authenticationFailure("persefonia_oidc_invalid_groups", "OIDC groups are invalid");
        }
        Set<OidcGroup> groups = new HashSet<>();
        for (Object value : values) {
            if (!(value instanceof String group)) {
                throw authenticationFailure("persefonia_oidc_invalid_groups", "OIDC groups are invalid");
            }
            try {
                groups.add(OidcGroup.of(group));
            } catch (IllegalArgumentException exception) {
                throw authenticationFailure("persefonia_oidc_invalid_groups", "OIDC groups are invalid");
            }
        }
        return Set.copyOf(groups);
    }

    private static String requiredString(Map<String, Object> claims, String name, String missingCode) {
        if (!claims.containsKey(name) || claims.get(name) == null) {
            throw authenticationFailure(missingCode, "Required OIDC identity claim is missing");
        }
        String value = nonBlankString(claims, name);
        if (value == null) {
            throw authenticationFailure(missingCode, "Required OIDC identity claim is missing");
        }
        return value;
    }

    private static String nonBlankString(Map<String, Object> claims, String name) {
        Object value = claims.get(name);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text)) {
            throw authenticationFailure("persefonia_oidc_invalid_claims", "OIDC claims are invalid");
        }
        return text.isBlank() ? null : text;
    }

    private static OAuth2AuthenticationException authenticationFailure(String code, String description) {
        return new OAuth2AuthenticationException(new OAuth2Error(code, description, null));
    }
}
