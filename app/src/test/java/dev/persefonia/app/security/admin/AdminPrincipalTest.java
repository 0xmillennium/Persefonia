package dev.persefonia.app.security.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.persefonia.identityaccess.domain.admin.AdminAccountId;
import dev.persefonia.identityaccess.domain.admin.AdminAccountStatus;
import dev.persefonia.identityaccess.domain.admin.AdminRole;
import dev.persefonia.identityaccess.domain.admin.DisplayName;
import dev.persefonia.identityaccess.domain.admin.EmailAddress;
import dev.persefonia.identityaccess.domain.admin.NormalizedEmailAddress;
import dev.persefonia.identityaccess.domain.admin.OidcSubject;

class AdminPrincipalTest {
    @Test
    void requiresAccountId() {
        assertThatThrownBy(() -> principal(null, Set.of(AdminRole.OWNER), AdminAccountStatus.ACTIVE))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void requiresOidcSubject() {
        assertThatThrownBy(() -> new AdminPrincipal(id(), null, email(), normalizedEmail(), displayName(),
                Set.of(AdminRole.OWNER), AdminAccountStatus.ACTIVE)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void requiresEmail() {
        assertThatThrownBy(() -> new AdminPrincipal(id(), subject(), null, normalizedEmail(), displayName(),
                Set.of(AdminRole.OWNER), AdminAccountStatus.ACTIVE)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void requiresNormalizedEmail() {
        assertThatThrownBy(() -> new AdminPrincipal(id(), subject(), email(), null, displayName(),
                Set.of(AdminRole.OWNER), AdminAccountStatus.ACTIVE)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void requiresDisplayName() {
        assertThatThrownBy(() -> new AdminPrincipal(id(), subject(), email(), normalizedEmail(), null,
                Set.of(AdminRole.OWNER), AdminAccountStatus.ACTIVE)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void requiresRoles() {
        assertThatThrownBy(() -> principal(id(), null, AdminAccountStatus.ACTIVE))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void requiresActiveStatus() {
        assertThatThrownBy(() -> principal(id(), Set.of(AdminRole.OWNER), AdminAccountStatus.DISABLED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsEmptyRolesForActivePrincipal() {
        assertThatThrownBy(() -> principal(id(), Set.of(), AdminAccountStatus.ACTIVE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rolesAreDefensivelyCopied() {
        Set<AdminRole> roles = new HashSet<>(Set.of(AdminRole.OWNER));
        AdminPrincipal principal = principal(id(), roles, AdminAccountStatus.ACTIVE);

        roles.add(AdminRole.EDITOR);

        assertThat(principal.roles()).containsExactly(AdminRole.OWNER);
        assertThatThrownBy(() -> principal.roles().add(AdminRole.EDITOR))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void hasRoleWorks() {
        assertThat(principal(id(), Set.of(AdminRole.OWNER), AdminAccountStatus.ACTIVE).hasRole(AdminRole.OWNER))
                .isTrue();
    }

    @Test
    void isOwnerWorks() {
        assertThat(principal(id(), Set.of(AdminRole.OWNER), AdminAccountStatus.ACTIVE).isOwner()).isTrue();
    }

    @Test
    void isEditorWorks() {
        assertThat(principal(id(), Set.of(AdminRole.EDITOR), AdminAccountStatus.ACTIVE).isEditor()).isTrue();
    }

    @Test
    void storesNoTokenPasswordSessionCredentialFields() {
        assertThat(AdminPrincipal.class.getDeclaredFields())
                .extracting(Field::getName)
                .noneMatch(name -> name.toLowerCase().matches(".*(token|password|credential|session|securitycontext).*"));
    }

    static AdminPrincipal principal(AdminAccountId id, Set<AdminRole> roles, AdminAccountStatus status) {
        return new AdminPrincipal(id, subject(), email(), normalizedEmail(), displayName(), roles, status);
    }

    static AdminAccountId id() {
        return AdminAccountId.newId();
    }

    private static OidcSubject subject() {
        return OidcSubject.of("opaque-subject");
    }

    private static EmailAddress email() {
        return EmailAddress.of("admin@example.com");
    }

    private static NormalizedEmailAddress normalizedEmail() {
        return NormalizedEmailAddress.from(email());
    }

    private static DisplayName displayName() {
        return DisplayName.of("Admin");
    }
}
