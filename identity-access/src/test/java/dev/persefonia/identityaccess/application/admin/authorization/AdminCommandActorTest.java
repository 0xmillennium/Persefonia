package dev.persefonia.identityaccess.application.admin.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.RecordComponent;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.persefonia.identityaccess.domain.admin.AdminAccountId;
import dev.persefonia.identityaccess.domain.admin.AdminAccountStatus;
import dev.persefonia.identityaccess.domain.admin.AdminRole;

class AdminCommandActorTest {
    private static final AdminAccountId ACCOUNT_ID =
            AdminAccountId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    @Test
    void requiresAccountId() {
        assertThatThrownBy(() -> new AdminCommandActor(null, AdminAccountStatus.ACTIVE, Set.of(AdminRole.OWNER)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void requiresStatus() {
        assertThatThrownBy(() -> new AdminCommandActor(ACCOUNT_ID, null, Set.of(AdminRole.OWNER)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void requiresRoles() {
        assertThatThrownBy(() -> new AdminCommandActor(ACCOUNT_ID, AdminAccountStatus.ACTIVE, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullRole() {
        Set<AdminRole> roles = new HashSet<>();
        roles.add(AdminRole.OWNER);
        roles.add(null);

        assertThatThrownBy(() -> new AdminCommandActor(ACCOUNT_ID, AdminAccountStatus.ACTIVE, roles))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void defensivelyCopiesRoles() {
        Set<AdminRole> roles = new HashSet<>(Set.of(AdminRole.OWNER));
        AdminCommandActor actor = new AdminCommandActor(ACCOUNT_ID, AdminAccountStatus.ACTIVE, roles);

        roles.clear();

        assertThat(actor.roles()).containsExactly(AdminRole.OWNER);
        assertThatThrownBy(() -> actor.roles().add(AdminRole.EDITOR))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void activeActorRequiresNonEmptyRoles() {
        assertThatThrownBy(() -> new AdminCommandActor(ACCOUNT_ID, AdminAccountStatus.ACTIVE, Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("active");
    }

    @Test
    void disabledActorMayCarryRoles() {
        AdminCommandActor actor = new AdminCommandActor(
                ACCOUNT_ID,
                AdminAccountStatus.DISABLED,
                Set.of(AdminRole.OWNER));

        assertThat(actor.roles()).containsExactly(AdminRole.OWNER);
        assertThat(actor.isActive()).isFalse();
    }

    @Test
    void isOwnerReturnsTrueForOwner() {
        assertThat(actor(AdminRole.OWNER).isOwner()).isTrue();
    }

    @Test
    void isEditorReturnsTrueForEditor() {
        assertThat(actor(AdminRole.EDITOR).isEditor()).isTrue();
    }

    @Test
    void hasRoleWorks() {
        AdminCommandActor actor = actor(AdminRole.OWNER);

        assertThat(actor.hasRole(AdminRole.OWNER)).isTrue();
        assertThat(actor.hasRole(AdminRole.EDITOR)).isFalse();
    }

    @Test
    void storesNoEmailSubjectTokenPasswordSessionOrCredentialState() {
        Set<String> componentNames = Set.of(AdminCommandActor.class.getRecordComponents()).stream()
                .map(RecordComponent::getName)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());

        assertThat(componentNames).containsExactlyInAnyOrder("accountid", "status", "roles");
        assertThat(componentNames)
                .noneMatch(name -> name.contains("email"))
                .noneMatch(name -> name.contains("subject"))
                .noneMatch(name -> name.contains("oidc"))
                .noneMatch(name -> name.contains("token"))
                .noneMatch(name -> name.contains("password"))
                .noneMatch(name -> name.contains("credential"))
                .noneMatch(name -> name.contains("session"))
                .noneMatch(name -> name.contains("securitycontext"));
    }

    private static AdminCommandActor actor(AdminRole role) {
        return new AdminCommandActor(ACCOUNT_ID, AdminAccountStatus.ACTIVE, Set.of(role));
    }
}
