package dev.persefonia.app.security.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.persefonia.identityaccess.domain.admin.AdminAccountStatus;
import dev.persefonia.identityaccess.domain.admin.AdminRole;

class AdminAuthoritiesTest {
    @Test
    void ownerPrincipalGetsRoleAdminAndRoleOwner() {
        assertThat(authorityNames(Set.of(AdminRole.OWNER)))
                .containsExactly(AdminAuthorities.ROLE_ADMIN, AdminAuthorities.ROLE_OWNER);
    }

    @Test
    void editorPrincipalGetsRoleAdminAndRoleEditor() {
        assertThat(authorityNames(Set.of(AdminRole.EDITOR)))
                .containsExactly(AdminAuthorities.ROLE_ADMIN, AdminAuthorities.ROLE_EDITOR);
    }

    @Test
    void ownerEditorPrincipalGetsAllExpectedAuthorities() {
        assertThat(authorityNames(Set.of(AdminRole.OWNER, AdminRole.EDITOR)))
                .containsExactly(AdminAuthorities.ROLE_ADMIN, AdminAuthorities.ROLE_OWNER, AdminAuthorities.ROLE_EDITOR);
    }

    @Test
    void authoritiesAreDerivedFromLocalRolesOnly() {
        assertThat(authorityNames(Set.of(AdminRole.EDITOR))).doesNotContain("ROLE_OWNER", "providers");
    }

    private static Set<String> authorityNames(Set<AdminRole> roles) {
        return AdminAuthorities.from(AdminPrincipalTest.principal(
                        AdminPrincipalTest.id(), roles, AdminAccountStatus.ACTIVE))
                .stream()
                .map(authority -> authority.getAuthority())
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }
}
