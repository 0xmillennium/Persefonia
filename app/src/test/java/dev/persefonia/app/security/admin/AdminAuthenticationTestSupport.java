package dev.persefonia.app.security.admin;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

import dev.persefonia.app.security.oidc.PersefoniaOidcUser;
import dev.persefonia.identityaccess.domain.admin.AdminAccountId;
import dev.persefonia.identityaccess.domain.admin.AdminAccountStatus;
import dev.persefonia.identityaccess.domain.admin.AdminRole;
import dev.persefonia.identityaccess.domain.admin.DisplayName;
import dev.persefonia.identityaccess.domain.admin.EmailAddress;
import dev.persefonia.identityaccess.domain.admin.NormalizedEmailAddress;
import dev.persefonia.identityaccess.domain.admin.OidcSubject;

public final class AdminAuthenticationTestSupport {
    private static final Instant ISSUED_AT = Instant.parse("2026-06-11T08:00:00Z");

    private AdminAuthenticationTestSupport() {
    }

    public static OAuth2AuthenticationToken authentication(AdminRole... roles) {
        PersefoniaOidcUser user = oidcUser(Set.of(roles));
        return new OAuth2AuthenticationToken(user, user.getAuthorities(), "authelia");
    }

    public static PersefoniaOidcUser oidcUser(Set<AdminRole> roles) {
        OidcIdToken token = new OidcIdToken(
                "fake-id-token-value",
                ISSUED_AT,
                ISSUED_AT.plusSeconds(300),
                Map.of("sub", "opaque-subject", "email", "admin@example.com", "name", "Ada Admin"));
        DefaultOidcUser delegate = new DefaultOidcUser(Set.of(), token, "sub");
        return new PersefoniaOidcUser(delegate, principal(roles));
    }

    public static AdminPrincipal principal(Set<AdminRole> roles) {
        EmailAddress email = EmailAddress.of("admin@example.com");
        return new AdminPrincipal(
                AdminAccountId.newId(),
                OidcSubject.of("opaque-subject"),
                email,
                NormalizedEmailAddress.from(email),
                DisplayName.of("Ada Admin"),
                roles,
                AdminAccountStatus.ACTIVE);
    }
}
