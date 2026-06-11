package dev.persefonia.app.security.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import dev.persefonia.app.identityaccess.bootstrap.TransactionalAdminBootstrapGateway;
import dev.persefonia.identityaccess.application.admin.bootstrap.AdminBootstrapUseCase;
import dev.persefonia.identityaccess.domain.admin.AdminAccount;
import dev.persefonia.identityaccess.domain.admin.AdminAccountId;
import dev.persefonia.identityaccess.domain.admin.AdminAccountRepository;
import dev.persefonia.identityaccess.domain.admin.AdminRole;
import dev.persefonia.identityaccess.domain.admin.DisplayName;
import dev.persefonia.identityaccess.domain.admin.EmailAddress;
import dev.persefonia.identityaccess.domain.admin.NormalizedEmailAddress;
import dev.persefonia.identityaccess.domain.admin.OidcSubject;
import dev.persefonia.identityaccess.domain.admin.access.AdminAccessPolicy;

class PersefoniaOidcUserServiceTest {
    private static final Instant NOW = Instant.parse("2026-06-11T08:00:00Z");

    @Test
    void loadsDelegateOidcUserAndReturnsPersefoniaOidcUser() {
        var delegateUser = OidcTestFixtures.validUser();

        assertThat(ownerService(delegateUser).loadUser(null))
                .isInstanceOf(PersefoniaOidcUser.class)
                .extracting(user -> ((PersefoniaOidcUser) user).getIdToken())
                .isSameAs(delegateUser.getIdToken());
    }

    @Test
    void callsAdminBootstrapGatewayWithMappedClaims() {
        var user = (PersefoniaOidcUser) ownerService(OidcTestFixtures.validUser()).loadUser(null);

        assertThat(user.adminPrincipal().oidcSubject().value()).isEqualTo("opaque-subject");
        assertThat(user.adminPrincipal().email().value()).isEqualTo("admin@example.com");
        assertThat(user.adminPrincipal().displayName().value()).isEqualTo("Admin");
    }

    @Test
    void mapsBootstrappedOwnerToRoleAdminAndRoleOwner() {
        assertThat(authorities(ownerService(OidcTestFixtures.validUser()).loadUser(null)))
                .containsExactly("ROLE_ADMIN", "ROLE_OWNER");
    }

    @Test
    void mapsExistingEditorToRoleAdminAndRoleEditor() {
        var delegateUser = OidcTestFixtures.user(Map.of(
                "sub", "editor-subject", "email", "editor@example.com", "name", "Editor"));
        InMemoryRepository repository = new InMemoryRepository();
        repository.save(account("editor-subject", "editor@example.com", AdminRole.EDITOR));

        assertThat(authorities(service(delegateUser, repository, AdminAccessPolicy.of(Set.of(), Set.of(), true, false))
                        .loadUser(null)))
                .containsExactly("ROLE_ADMIN", "ROLE_EDITOR");
    }

    @Test
    void adminAccessDeniedBecomesOAuth2AuthenticationException() {
        assertThatThrownBy(() -> service(
                        OidcTestFixtures.validUser(),
                        new InMemoryRepository(),
                        AdminAccessPolicy.of(Set.of(), Set.of(), true, false))
                .loadUser(null))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class, exception -> {
                    assertThat(exception.getError().getErrorCode()).isEqualTo("persefonia_admin_access_denied");
                    assertThat(exception.getError().getDescription()).isEqualTo("Admin access denied");
                });
    }

    @Test
    void disabledAccountDenialBecomesOAuth2AuthenticationException() {
        InMemoryRepository repository = new InMemoryRepository();
        repository.save(account("opaque-subject", "admin@example.com", AdminRole.OWNER).disable(NOW.plusSeconds(1)));

        assertAccessDenied(service(
                OidcTestFixtures.validUser(),
                repository,
                AdminAccessPolicy.of(Set.of(), Set.of(), true, false)));
    }

    @Test
    void emailCollisionDenialBecomesOAuth2AuthenticationException() {
        InMemoryRepository repository = new InMemoryRepository();
        repository.save(account("other-subject", "admin@example.com", AdminRole.OWNER));

        assertAccessDenied(service(
                OidcTestFixtures.validUser(),
                repository,
                AdminAccessPolicy.of(Set.of(OidcSubject.of("opaque-subject")), Set.of(), true, true)));
    }

    @Test
    void oauth2ErrorDoesNotExposeSubjectOrEmail() {
        String subject = "sensitive-subject";
        String email = "sensitive@example.com";

        assertThatThrownBy(() -> service(
                        OidcTestFixtures.user(Map.of("sub", subject, "email", email)),
                        new InMemoryRepository(),
                        AdminAccessPolicy.of(Set.of(), Set.of(), true, false))
                .loadUser(null))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageNotContaining(subject)
                .hasMessageNotContaining(email);
    }

    @Test
    void invalidClaimsBecomeOAuth2AuthenticationException() {
        assertThatThrownBy(() -> ownerService(OidcTestFixtures.user(Map.of("sub", "subject", "email", "invalid")))
                        .loadUser(null))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }

    @Test
    void doesNotStoreTokenInAdminPrincipal() {
        var principal = ((PersefoniaOidcUser) ownerService(OidcTestFixtures.validUser()).loadUser(null)).adminPrincipal();

        assertThat(principal.getClass().getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .noneMatch(name -> name.toLowerCase().contains("token"));
    }

    private static PersefoniaOidcUserService ownerService(
            org.springframework.security.oauth2.core.oidc.user.OidcUser delegateUser) {
        return service(
                delegateUser,
                new InMemoryRepository(),
                AdminAccessPolicy.of(Set.of(OidcSubject.of("opaque-subject")), Set.of(), true, false));
    }

    private static PersefoniaOidcUserService service(
            org.springframework.security.oauth2.core.oidc.user.OidcUser delegateUser,
            AdminAccountRepository repository,
            AdminAccessPolicy policy) {
        AdminBootstrapUseCase useCase = new AdminBootstrapUseCase(
                repository,
                policy,
                () -> {
                },
                Clock.fixed(NOW, ZoneOffset.UTC));
        return new PersefoniaOidcUserService(
                new OidcClaimMapper(),
                new TransactionalAdminBootstrapGateway(useCase),
                request -> delegateUser);
    }

    private static AdminAccount account(String subject, String email, AdminRole role) {
        return AdminAccount.create(
                AdminAccountId.newId(),
                OidcSubject.of(subject),
                EmailAddress.of(email),
                DisplayName.of(role.name()),
                Set.of(role),
                NOW);
    }

    private static Set<String> authorities(org.springframework.security.oauth2.core.oidc.user.OidcUser user) {
        return user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private static void assertAccessDenied(PersefoniaOidcUserService service) {
        assertThatThrownBy(() -> service.loadUser(null))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class,
                        exception -> assertThat(exception.getError().getErrorCode())
                                .isEqualTo("persefonia_admin_access_denied"));
    }

    private static final class InMemoryRepository implements AdminAccountRepository {
        private final Map<AdminAccountId, AdminAccount> accounts = new LinkedHashMap<>();

        @Override
        public AdminAccount save(AdminAccount account) {
            accounts.put(account.id(), account);
            return account;
        }

        @Override
        public Optional<AdminAccount> findById(AdminAccountId id) {
            return Optional.ofNullable(accounts.get(id));
        }

        @Override
        public Optional<AdminAccount> findByOidcSubject(OidcSubject oidcSubject) {
            return accounts.values().stream().filter(account -> account.oidcSubject().equals(oidcSubject)).findFirst();
        }

        @Override
        public Optional<AdminAccount> findByEmail(EmailAddress email) {
            return findByNormalizedEmail(NormalizedEmailAddress.from(email));
        }

        @Override
        public Optional<AdminAccount> findByNormalizedEmail(NormalizedEmailAddress normalizedEmail) {
            return accounts.values().stream()
                    .filter(account -> account.normalizedEmail().equals(normalizedEmail))
                    .findFirst();
        }

        @Override
        public boolean existsActiveOwner() {
            return accounts.values().stream().anyMatch(account -> account.hasRole(AdminRole.OWNER));
        }

        @Override
        public long countAll() {
            return accounts.size();
        }
    }
}
