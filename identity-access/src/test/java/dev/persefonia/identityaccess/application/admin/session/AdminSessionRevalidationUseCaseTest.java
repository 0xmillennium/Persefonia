package dev.persefonia.identityaccess.application.admin.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.persefonia.identityaccess.domain.admin.AdminAccount;
import dev.persefonia.identityaccess.domain.admin.AdminAccountId;
import dev.persefonia.identityaccess.domain.admin.AdminAccountRepository;
import dev.persefonia.identityaccess.domain.admin.AdminRole;
import dev.persefonia.identityaccess.domain.admin.DisplayName;
import dev.persefonia.identityaccess.domain.admin.EmailAddress;
import dev.persefonia.identityaccess.domain.admin.NormalizedEmailAddress;
import dev.persefonia.identityaccess.domain.admin.OidcGroup;
import dev.persefonia.identityaccess.domain.admin.OidcSubject;
import dev.persefonia.identityaccess.domain.admin.access.AdminAccessPolicy;
import dev.persefonia.identityaccess.domain.admin.access.AdminAccessDeniedException;
import dev.persefonia.identityaccess.domain.admin.access.AdminAccessDenialReason;
import dev.persefonia.identityaccess.domain.admin.access.AdminIdentityClaims;

class AdminSessionRevalidationUseCaseTest {
    private static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");
    private static final AdminIdentityClaims CLAIMS = AdminIdentityClaims.of(OidcSubject.of("subject"),
            EmailAddress.of("admin@example.com"), DisplayName.of("Admin"), Set.of(OidcGroup.of("admin")));

    @Test
    void returnsCurrentRolesWithoutWritingOrUpdatingLogin() {
        for (AdminRole role : AdminRole.values()) {
            AdminAccount account = account(role).recordSuccessfulLogin(NOW);
            assertThat(useCase(account).revalidate(CLAIMS)).isSameAs(account);
            assertThat(account.lastLoginAt()).contains(NOW);
        }
    }

    @Test
    void deniesMissingGroupMissingAccountAndDisabledAccount() {
        assertDenied(useCase(account(AdminRole.OWNER)), AdminIdentityClaims.of(CLAIMS.oidcSubject(), CLAIMS.email(),
                CLAIMS.displayName(), Set.of()), AdminAccessDenialReason.REQUIRED_OIDC_GROUP_MISSING);
        assertDenied(useCase(null), CLAIMS, AdminAccessDenialReason.ADMIN_ACCOUNT_NOT_FOUND);
        assertDenied(useCase(account(AdminRole.OWNER).disable(NOW)), CLAIMS, AdminAccessDenialReason.DISABLED_ACCOUNT);
    }

    private static void assertDenied(AdminSessionRevalidationUseCase useCase, AdminIdentityClaims claims,
            AdminAccessDenialReason reason) {
        assertThatThrownBy(() -> useCase.revalidate(claims)).isInstanceOfSatisfying(AdminAccessDeniedException.class,
                exception -> assertThat(exception.reason()).isEqualTo(reason));
    }

    private static AdminAccount account(AdminRole role) {
        return AdminAccount.create(AdminAccountId.newId(), CLAIMS.oidcSubject(), CLAIMS.email(), CLAIMS.displayName(),
                Set.of(role), NOW);
    }

    private static AdminSessionRevalidationUseCase useCase(AdminAccount account) {
        AdminAccountRepository repository = new AdminAccountRepository() {
            public AdminAccount save(AdminAccount value) { throw new AssertionError("revalidation must not save"); }
            public Optional<AdminAccount> findByOidcSubject(OidcSubject subject) { return Optional.ofNullable(account); }
            public Optional<AdminAccount> findById(AdminAccountId id) { throw new AssertionError(); }
            public Optional<AdminAccount> findByEmail(EmailAddress email) { throw new AssertionError(); }
            public Optional<AdminAccount> findByNormalizedEmail(NormalizedEmailAddress email) { throw new AssertionError(); }
            public boolean existsActiveOwner() { throw new AssertionError(); }
            public long countAll() { throw new AssertionError(); }
        };
        return new AdminSessionRevalidationUseCase(repository, AdminAccessPolicy.of(OidcGroup.of("admin"), true, true));
    }
}
