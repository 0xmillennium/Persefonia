package dev.persefonia.app.security.oidc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import dev.persefonia.app.security.admin.AdminPrincipal;
import dev.persefonia.identityaccess.domain.admin.AdminAccountId;
import dev.persefonia.identityaccess.domain.admin.AdminAccountStatus;
import dev.persefonia.identityaccess.domain.admin.AdminRole;
import dev.persefonia.identityaccess.domain.admin.DisplayName;
import dev.persefonia.identityaccess.domain.admin.EmailAddress;
import dev.persefonia.identityaccess.domain.admin.NormalizedEmailAddress;
import dev.persefonia.identityaccess.domain.admin.OidcSubject;

class PersefoniaOidcUserTest {
    @Test
    void delegatesClaimsToOriginalOidcUser() {
        var delegate = OidcTestFixtures.validUser();

        assertThat(user(delegate, Set.of(AdminRole.EDITOR)).getClaims()).isSameAs(delegate.getClaims());
        assertThat(user(delegate, Set.of(AdminRole.EDITOR)).getAttributes()).isSameAs(delegate.getAttributes());
        assertThat(user(delegate, Set.of(AdminRole.EDITOR)).getIdToken()).isSameAs(delegate.getIdToken());
    }

    @Test
    void returnsLocalAdminAuthorities() {
        assertThat(authorities(user(OidcTestFixtures.validUser(), Set.of(AdminRole.OWNER))))
                .containsExactly("ROLE_ADMIN", "ROLE_OWNER");
    }

    @Test
    void getNameUsesLocalAdminAccountId() {
        AdminPrincipal principal = principal(Set.of(AdminRole.OWNER));

        assertThat(new PersefoniaOidcUser(OidcTestFixtures.validUser(), principal).getName())
                .isEqualTo(principal.accountId().value().toString());
    }

    @Test
    void exposesAdminPrincipal() {
        AdminPrincipal principal = principal(Set.of(AdminRole.OWNER));

        assertThat(new PersefoniaOidcUser(OidcTestFixtures.validUser(), principal).adminPrincipal()).isSameAs(principal);
    }

    @Test
    void doesNotUseProviderAuthoritiesForAdminRoles() {
        assertThat(authorities(user(OidcTestFixtures.validUser(), Set.of(AdminRole.EDITOR))))
                .containsExactly("ROLE_ADMIN", "ROLE_EDITOR")
                .doesNotContain("ROLE_PROVIDER_OWNER", "ROLE_OWNER");
    }

    private static PersefoniaOidcUser user(
            org.springframework.security.oauth2.core.oidc.user.OidcUser delegate,
            Set<AdminRole> roles) {
        return new PersefoniaOidcUser(delegate, principal(roles));
    }

    private static AdminPrincipal principal(Set<AdminRole> roles) {
        EmailAddress email = EmailAddress.of("admin@example.com");
        return new AdminPrincipal(
                AdminAccountId.newId(),
                OidcSubject.of("opaque-subject"),
                email,
                NormalizedEmailAddress.from(email),
                DisplayName.of("Admin"),
                roles,
                AdminAccountStatus.ACTIVE);
    }

    private static Set<String> authorities(PersefoniaOidcUser user) {
        return user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }
}
