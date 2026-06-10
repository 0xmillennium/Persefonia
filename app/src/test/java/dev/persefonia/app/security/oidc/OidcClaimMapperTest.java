package dev.persefonia.app.security.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

class OidcClaimMapperTest {
    private final OidcClaimMapper mapper = new OidcClaimMapper();

    @Test
    void mapsSubjectEmailAndName() {
        var claims = mapper.toAdminIdentityClaims(OidcTestFixtures.validUser());

        assertThat(claims.oidcSubject().value()).isEqualTo("opaque-subject");
        assertThat(claims.email().value()).isEqualTo("admin@example.com");
        assertThat(claims.displayName().value()).isEqualTo("Admin");
    }

    @Test
    void usesPreferredUsernameWhenNameMissing() {
        var claims = mapper.toAdminIdentityClaims(OidcTestFixtures.user(Map.of(
                "sub", "subject", "email", "admin@example.com", "preferred_username", "preferred")));

        assertThat(claims.displayName().value()).isEqualTo("preferred");
    }

    @Test
    void usesEmailLocalPartWhenDisplayNameClaimsMissing() {
        var claims = mapper.toAdminIdentityClaims(
                OidcTestFixtures.user(Map.of("sub", "subject", "email", "local.part@example.com")));

        assertThat(claims.displayName().value()).isEqualTo("local.part");
    }

    @Test
    void rejectsMissingSubject() {
        assertError(Map.of("sub", " ", "email", "admin@example.com"), "persefonia_oidc_missing_subject");
    }

    @Test
    void rejectsMissingEmail() {
        assertError(Map.of("sub", "subject"), "persefonia_oidc_missing_email");
    }

    @Test
    void rejectsInvalidEmail() {
        assertError(Map.of("sub", "subject", "email", "invalid"), "persefonia_oidc_invalid_claims");
    }

    @Test
    void rejectsEmailVerifiedFalse() {
        assertError(
                Map.of("sub", "subject", "email", "admin@example.com", "email_verified", false),
                "persefonia_oidc_unverified_email");
    }

    @Test
    void acceptsMissingEmailVerifiedClaim() {
        assertThat(mapper.toAdminIdentityClaims(
                        OidcTestFixtures.user(Map.of("sub", "subject", "email", "admin@example.com")))
                .email().value()).isEqualTo("admin@example.com");
    }

    @Test
    void preservesOpaqueSubject() {
        String subject = "Issuer/Users:OpaqueCase";
        assertThat(mapper.toAdminIdentityClaims(
                        OidcTestFixtures.user(Map.of("sub", subject, "email", "admin@example.com")))
                .oidcSubject().value()).isEqualTo(subject);
    }

    @Test
    void doesNotExposeRawClaimsInExceptionMessage() {
        String subject = "sensitive-subject";
        String email = "sensitive@example.com";

        assertThatThrownBy(() -> mapper.toAdminIdentityClaims(
                        OidcTestFixtures.user(Map.of("sub", subject, "email", email, "email_verified", false))))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageNotContaining(subject)
                .hasMessageNotContaining(email)
                .hasMessageNotContaining("fake-id-token-value");
    }

    private void assertError(Map<String, Object> claims, String code) {
        Map<String, Object> mutableClaims = new HashMap<>(claims);
        assertThatThrownBy(() -> mapper.toAdminIdentityClaims(OidcTestFixtures.user(mutableClaims)))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class,
                        exception -> assertThat(exception.getError().getErrorCode()).isEqualTo(code));
    }
}
