package dev.persefonia.identityaccess.application.admin.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import dev.persefonia.identityaccess.domain.admin.OidcSubject;
import dev.persefonia.identityaccess.domain.admin.access.AdminAccessDeniedException;
import dev.persefonia.identityaccess.domain.admin.access.AdminAccessDenialReason;
import dev.persefonia.identityaccess.domain.admin.access.AdminAccessPolicy;
import dev.persefonia.identityaccess.domain.admin.access.AdminIdentityClaims;

class AdminBootstrapUseCaseTest {
    private static final Instant NOW = Instant.parse("2026-06-11T08:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final AdminIdentityClaims OWNER_CLAIMS = claims("owner-subject", "Owner@Example.COM", "Owner");

    @Test
    void firstAllowlistedSubjectBootstrapsActiveOwner() {
        AdminBootstrapResult result = service(subjectPolicy(OWNER_CLAIMS, true, false), new InMemoryRepository())
                .resolveOrBootstrap(OWNER_CLAIMS);

        assertInitialOwner(result);
    }

    @Test
    void firstAllowlistedEmailBootstrapsActiveOwner() {
        AdminAccessPolicy policy = AdminAccessPolicy.of(
                Set.of(),
                Set.of(NormalizedEmailAddress.from(OWNER_CLAIMS.email())),
                true,
                false);

        assertInitialOwner(service(policy, new InMemoryRepository()).resolveOrBootstrap(OWNER_CLAIMS));
    }

    @Test
    void unallowlistedIdentityCannotBootstrap() {
        assertDenied(
                () -> service(emptyPolicy(), new InMemoryRepository()).resolveOrBootstrap(OWNER_CLAIMS),
                AdminAccessDenialReason.NOT_ALLOWLISTED,
                OWNER_CLAIMS);
    }

    @Test
    void bootstrapDisabledRejectsFirstAllowlistedIdentity() {
        assertDenied(
                () -> service(subjectPolicy(OWNER_CLAIMS, false, false), new InMemoryRepository())
                        .resolveOrBootstrap(OWNER_CLAIMS),
                AdminAccessDenialReason.INITIAL_OWNER_BOOTSTRAP_DISABLED,
                OWNER_CLAIMS);
    }

    @Test
    void existingActiveAccountCanResolveAndUpdatesLastLogin() {
        InMemoryRepository repository = new InMemoryRepository();
        AdminAccount existing = account(OWNER_CLAIMS, AdminRole.OWNER);
        repository.save(existing);

        AdminBootstrapResult result = service(emptyPolicy(), repository).resolveOrBootstrap(OWNER_CLAIMS);

        assertThat(result.outcome()).isEqualTo(AdminBootstrapOutcome.EXISTING_ACCOUNT);
        assertThat(result.account().lastLoginAt()).contains(NOW);
    }

    @Test
    void existingDisabledAccountIsRejected() {
        InMemoryRepository repository = new InMemoryRepository();
        repository.save(account(OWNER_CLAIMS, AdminRole.OWNER).disable(NOW.plusSeconds(1)));

        assertDenied(
                () -> service(emptyPolicy(), repository).resolveOrBootstrap(OWNER_CLAIMS),
                AdminAccessDenialReason.DISABLED_ACCOUNT,
                OWNER_CLAIMS);
    }

    @Test
    void emailAlreadyBoundToDifferentSubjectIsRejected() {
        InMemoryRepository repository = new InMemoryRepository();
        repository.save(account(claims("first-subject", "shared@example.com", "First"), AdminRole.OWNER));
        AdminIdentityClaims collision = claims("second-subject", "SHARED@example.com", "Second");

        assertDenied(
                () -> service(subjectPolicy(collision, true, true), repository).resolveOrBootstrap(collision),
                AdminAccessDenialReason.EMAIL_ALREADY_BOUND,
                collision);
    }

    @Test
    void secondAllowlistedIdentityIsRejectedByDefaultAfterBootstrap() {
        InMemoryRepository repository = new InMemoryRepository();
        repository.save(account(OWNER_CLAIMS, AdminRole.OWNER));
        AdminIdentityClaims second = claims("editor-subject", "editor@example.com", "Editor");

        assertDenied(
                () -> service(subjectPolicy(second, true, false), repository).resolveOrBootstrap(second),
                AdminAccessDenialReason.AUTOMATIC_PROVISIONING_DISABLED,
                second);
    }

    @Test
    void automaticProvisioningCreatesActiveEditorWhenEnabled() {
        InMemoryRepository repository = new InMemoryRepository();
        repository.save(account(OWNER_CLAIMS, AdminRole.OWNER));
        AdminIdentityClaims editor = claims("editor-subject", "editor@example.com", "Editor");

        AdminBootstrapResult result = service(subjectPolicy(editor, true, true), repository).resolveOrBootstrap(editor);

        assertThat(result.outcome()).isEqualTo(AdminBootstrapOutcome.AUTOMATICALLY_PROVISIONED);
        assertThat(result.account().isActive()).isTrue();
        assertThat(result.account().roles()).containsExactly(AdminRole.EDITOR);
        assertThat(result.account().roles()).doesNotContain(AdminRole.OWNER);
    }

    @Test
    void publicSelfRegistrationIsImpossibleWithoutAllowlist() {
        AdminIdentityClaims publicIdentity = claims("public-subject", "public@example.com", "Public");

        assertDenied(
                () -> service(emptyPolicy(), new InMemoryRepository()).resolveOrBootstrap(publicIdentity),
                AdminAccessDenialReason.NOT_ALLOWLISTED,
                publicIdentity);
    }

    @Test
    void lockIsAcquired() {
        RecordingLock lock = new RecordingLock(new ArrayList<>());

        service(subjectPolicy(OWNER_CLAIMS, true, false), new InMemoryRepository(), lock)
                .resolveOrBootstrap(OWNER_CLAIMS);

        assertThat(lock.acquired()).isTrue();
    }

    @Test
    void lockIsAcquiredBeforeRepositoryMutationOrProvisioning() {
        List<String> operations = new ArrayList<>();

        service(
                        subjectPolicy(OWNER_CLAIMS, true, false),
                        new InMemoryRepository(operations),
                        new RecordingLock(operations))
                .resolveOrBootstrap(OWNER_CLAIMS);

        assertThat(operations).isNotEmpty();
        assertThat(operations.getFirst()).isEqualTo("lock.acquire");
        assertThat(operations).containsSubsequence("lock.acquire", "repository.findByOidcSubject", "repository.save");
    }

    private static AdminBootstrapUseCase service(AdminAccessPolicy policy, AdminAccountRepository repository) {
        return service(policy, repository, () -> {
        });
    }

    private static AdminBootstrapUseCase service(
            AdminAccessPolicy policy,
            AdminAccountRepository repository,
            AdminBootstrapLock lock) {
        return new AdminBootstrapUseCase(repository, policy, lock, CLOCK);
    }

    private static AdminAccessPolicy subjectPolicy(AdminIdentityClaims claims, boolean bootstrap, boolean provisioning) {
        return AdminAccessPolicy.of(Set.of(claims.oidcSubject()), Set.of(), bootstrap, provisioning);
    }

    private static AdminAccessPolicy emptyPolicy() {
        return AdminAccessPolicy.of(Set.of(), Set.of(), true, false);
    }

    private static AdminIdentityClaims claims(String subject, String email, String displayName) {
        return AdminIdentityClaims.of(
                OidcSubject.of(subject),
                EmailAddress.of(email),
                DisplayName.of(displayName));
    }

    private static AdminAccount account(AdminIdentityClaims claims, AdminRole role) {
        return AdminAccount.create(
                AdminAccountId.newId(),
                claims.oidcSubject(),
                claims.email(),
                claims.displayName(),
                Set.of(role),
                NOW);
    }

    private static void assertInitialOwner(AdminBootstrapResult result) {
        assertThat(result.outcome()).isEqualTo(AdminBootstrapOutcome.INITIAL_OWNER_BOOTSTRAPPED);
        assertThat(result.account().isActive()).isTrue();
        assertThat(result.account().roles()).containsExactly(AdminRole.OWNER);
        assertThat(result.account().lastLoginAt()).contains(NOW);
    }

    private static void assertDenied(
            ThrowingCall call,
            AdminAccessDenialReason reason,
            AdminIdentityClaims claims) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(AdminAccessDeniedException.class, exception ->
                        assertThat(exception.reason()).isEqualTo(reason))
                .hasMessageNotContaining(claims.oidcSubject().value())
                .hasMessageNotContaining(claims.email().value());
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run();
    }

    private static final class InMemoryRepository implements AdminAccountRepository {
        private final Map<AdminAccountId, AdminAccount> accounts = new LinkedHashMap<>();
        private final List<String> operations;

        private InMemoryRepository() {
            this(new ArrayList<>());
        }

        private InMemoryRepository(List<String> operations) {
            this.operations = operations;
        }

        @Override
        public AdminAccount save(AdminAccount account) {
            operations.add("repository.save");
            accounts.put(account.id(), account);
            return account;
        }

        @Override
        public Optional<AdminAccount> findById(AdminAccountId id) {
            return Optional.ofNullable(accounts.get(id));
        }

        @Override
        public Optional<AdminAccount> findByOidcSubject(OidcSubject oidcSubject) {
            operations.add("repository.findByOidcSubject");
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
            return accounts.values().stream()
                    .anyMatch(account -> account.isActive() && account.hasRole(AdminRole.OWNER));
        }

        @Override
        public long countAll() {
            return accounts.size();
        }
    }

    private static final class RecordingLock implements AdminBootstrapLock {
        private final List<String> operations;
        private boolean acquired;

        private RecordingLock(List<String> operations) {
            this.operations = operations;
        }

        @Override
        public void acquire() {
            acquired = true;
            operations.add("lock.acquire");
        }

        private boolean acquired() {
            return acquired;
        }
    }
}
