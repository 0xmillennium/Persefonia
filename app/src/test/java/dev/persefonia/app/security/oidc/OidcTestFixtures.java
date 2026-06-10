package dev.persefonia.app.security.oidc;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

final class OidcTestFixtures {
    private static final Instant ISSUED_AT = Instant.parse("2026-06-11T08:00:00Z");

    private OidcTestFixtures() {
    }

    static OidcUser user(Map<String, Object> claims) {
        Map<String, Object> completeClaims = new LinkedHashMap<>(claims);
        completeClaims.putIfAbsent("sub", "opaque-subject");
        OidcIdToken token = new OidcIdToken(
                "fake-id-token-value",
                ISSUED_AT,
                ISSUED_AT.plusSeconds(300),
                completeClaims);
        return new DefaultOidcUser(Set.of(new SimpleGrantedAuthority("ROLE_PROVIDER_OWNER")), token, "sub");
    }

    static OidcUser validUser() {
        return user(Map.of(
                "sub", "opaque-subject",
                "email", "admin@example.com",
                "name", "Admin",
                "email_verified", true));
    }
}
