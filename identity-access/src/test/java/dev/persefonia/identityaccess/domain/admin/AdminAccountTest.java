package dev.persefonia.identityaccess.domain.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class AdminAccountTest {
    private static final Instant CREATED_AT = Instant.parse("2026-06-10T08:00:00Z");

    @Test
    void createActiveAdminAccountWithOwnerRole() {
        AdminAccount account = createAccount(Set.of(AdminRole.OWNER));

        assertThat(account.status()).isEqualTo(AdminAccountStatus.ACTIVE);
        assertThat(account.isActive()).isTrue();
        assertThat(account.hasRole(AdminRole.OWNER)).isTrue();
        assertThat(account.version()).isEqualTo(Version.initial());
        assertThat(account.lastLoginAt()).isEmpty();
        assertThat(account.canReceiveAdminSession()).isTrue();
    }

    @Test
    void activeAccountRequiresAtLeastOneRole() {
        assertThatThrownBy(() -> createAccount(Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one role");
    }

    @Test
    void normalizedEmailIsDerivedFromEmail() {
        AdminAccount account = createAccount(Set.of(AdminRole.OWNER));

        assertThat(account.email().value()).isEqualTo("Owner@Example.COM");
        assertThat(account.normalizedEmail().value()).isEqualTo("owner@example.com");
    }

    @Test
    void disabledAccountCannotReceiveAdminSession() {
        AdminAccount disabled = createAccount(Set.of(AdminRole.OWNER)).disable(CREATED_AT.plusSeconds(1));

        assertThat(disabled.isDisabled()).isTrue();
        assertThat(disabled.canReceiveAdminSession()).isFalse();
    }

    @Test
    void recordSuccessfulLoginRequiresActiveAccount() {
        AdminAccount disabled = createAccount(Set.of(AdminRole.OWNER)).disable(CREATED_AT.plusSeconds(1));

        assertThatThrownBy(() -> disabled.recordSuccessfulLogin(CREATED_AT.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void recordSuccessfulLoginUpdatesLastLoginAndVersion() {
        Instant loginAt = CREATED_AT.plusSeconds(60);

        AdminAccount loggedIn = createAccount(Set.of(AdminRole.OWNER)).recordSuccessfulLogin(loginAt);

        assertThat(loggedIn.lastLoginAt()).contains(loginAt);
        assertThat(loggedIn.updatedAt()).isEqualTo(loginAt);
        assertThat(loggedIn.version()).isEqualTo(Version.of(1));
    }

    @Test
    void disableChangesStatusAndIncrementsVersion() {
        Instant disabledAt = CREATED_AT.plusSeconds(60);

        AdminAccount disabled = createAccount(Set.of(AdminRole.OWNER)).disable(disabledAt);

        assertThat(disabled.status()).isEqualTo(AdminAccountStatus.DISABLED);
        assertThat(disabled.updatedAt()).isEqualTo(disabledAt);
        assertThat(disabled.version()).isEqualTo(Version.of(1));
        assertThat(disabled.disable(disabledAt.plusSeconds(1))).isSameAs(disabled);
    }

    @Test
    void rolesAreDefensivelyCopied() {
        Set<AdminRole> roles = new HashSet<>();
        roles.add(AdminRole.OWNER);

        AdminAccount account = createAccount(roles);
        roles.clear();

        assertThat(account.roles()).containsExactly(AdminRole.OWNER);
        assertThatThrownBy(() -> account.roles().add(AdminRole.EDITOR))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rehydrateRejectsMismatchedNormalizedEmail() {
        assertThatThrownBy(() -> rehydrate(
                NormalizedEmailAddress.of("different@example.com"),
                AdminAccountStatus.ACTIVE,
                null,
                CREATED_AT,
                CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("derived from email");
    }

    @Test
    void rehydrateRejectsUpdatedAtBeforeCreatedAt() {
        assertThatThrownBy(() -> rehydrate(
                NormalizedEmailAddress.of("owner@example.com"),
                AdminAccountStatus.ACTIVE,
                null,
                CREATED_AT,
                CREATED_AT.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("updatedAt");
    }

    @Test
    void rehydrateRejectsLastLoginBeforeCreatedAt() {
        assertThatThrownBy(() -> rehydrate(
                NormalizedEmailAddress.of("owner@example.com"),
                AdminAccountStatus.ACTIVE,
                CREATED_AT.minusSeconds(1),
                CREATED_AT,
                CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lastLoginAt");
    }

    private static AdminAccount createAccount(Set<AdminRole> roles) {
        return AdminAccount.create(
                AdminAccountId.newId(),
                OidcSubject.of("opaque-owner-subject"),
                EmailAddress.of("Owner@Example.COM"),
                DisplayName.of("Owner"),
                roles,
                CREATED_AT);
    }

    private static AdminAccount rehydrate(
            NormalizedEmailAddress normalizedEmail,
            AdminAccountStatus status,
            Instant lastLoginAt,
            Instant createdAt,
            Instant updatedAt) {
        return AdminAccount.rehydrate(
                AdminAccountId.newId(),
                OidcSubject.of("opaque-owner-subject"),
                EmailAddress.of("Owner@Example.COM"),
                normalizedEmail,
                DisplayName.of("Owner"),
                Set.of(AdminRole.OWNER),
                status,
                lastLoginAt,
                createdAt,
                updatedAt,
                Version.initial());
    }
}
