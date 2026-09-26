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

    @Test
    void groupsAreStrictTypedImmutableAndCasePreserving() {
        var user = OidcTestFixtures.user(Map.of("email", "admin@example.com",
                "groups", java.util.List.of("admin", "admin", "Admin")));
        assertThat(mapper.toAdminIdentityClaims(user).oidcGroups())
                .extracting(group -> group.value()).containsExactlyInAnyOrder("admin", "Admin");
        assertThat(mapper.toAdminIdentityClaims(OidcTestFixtures.user(Map.of("email", "admin@example.com",
                "groups", java.util.List.of()))).oidcGroups()).isEmpty();
        for (Object invalid : java.util.List.of("admin", java.util.List.of("admin", 5),
                java.util.List.of(" "), java.util.List.of("ad\nmin"))) {
            assertError(Map.of("email", "admin@example.com", "groups", invalid), "persefonia_oidc_invalid_groups");
        }
        assertThatThrownBy(() -> mapper.toAdminIdentityClaims(Map.of("sub", "subject", "email", "admin@example.com")))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class, exception ->
                        assertThat(exception.getError().getErrorCode()).isEqualTo("persefonia_oidc_missing_groups"));
    }

    @Test
    void freshUserInfoOverridesIdTokenGroupsAndIdentityFields() {
        var stale = OidcTestFixtures.validUser();
        var fresh = new org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser(
                stale.getAuthorities(), stale.getIdToken(), new org.springframework.security.oauth2.core.oidc.OidcUserInfo(
                Map.of("sub", "opaque-subject", "email", "fresh@example.com", "groups", java.util.List.of("user"))));
        var claims = mapper.toAdminIdentityClaims(fresh);
        assertThat(claims.email().value()).isEqualTo("fresh@example.com");
        assertThat(claims.oidcGroups()).extracting(group -> group.value()).containsExactly("user");
    }

    @Test
    void invalidGroupsDoNotLeakIdentityGroupOrToken() {
        assertThatThrownBy(() -> mapper.toAdminIdentityClaims(Map.of("sub", "sensitive-subject",
                "email", "sensitive@example.com", "groups", java.util.List.of("sensitive-group", 1))))
                .hasMessageNotContaining("sensitive-subject").hasMessageNotContaining("sensitive@example.com")
                .hasMessageNotContaining("sensitive-group");
    }

    private void assertError(Map<String, Object> claims, String code) {
        Map<String, Object> mutableClaims = new HashMap<>(claims);
        assertThatThrownBy(() -> mapper.toAdminIdentityClaims(OidcTestFixtures.user(mutableClaims)))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class,
                        exception -> assertThat(exception.getError().getErrorCode()).isEqualTo(code));
    }
}
