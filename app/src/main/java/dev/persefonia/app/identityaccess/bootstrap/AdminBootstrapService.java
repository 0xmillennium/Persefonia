package dev.persefonia.app.identityaccess.bootstrap;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.persefonia.identityaccess.domain.admin.AdminAccount;
import dev.persefonia.identityaccess.domain.admin.AdminAccountId;
import dev.persefonia.identityaccess.domain.admin.AdminAccountRepository;
import dev.persefonia.identityaccess.domain.admin.AdminRole;
import dev.persefonia.identityaccess.domain.admin.NormalizedEmailAddress;
import dev.persefonia.identityaccess.domain.admin.access.AdminAccessDeniedException;
import dev.persefonia.identityaccess.domain.admin.access.AdminAccessDenialReason;
import dev.persefonia.identityaccess.domain.admin.access.AdminAccessPolicy;
import dev.persefonia.identityaccess.domain.admin.access.AdminIdentityClaims;

@Service
@Lazy
public class AdminBootstrapService {
    private final AdminAccountRepository repository;
    private final AdminAccessPolicy accessPolicy;
    private final AdminBootstrapLock bootstrapLock;
    private final Clock clock;

    public AdminBootstrapService(
            AdminAccountRepository repository,
            AdminAccessPolicy accessPolicy,
            AdminBootstrapLock bootstrapLock,
            Clock clock) {
        this.repository = repository;
        this.accessPolicy = accessPolicy;
        this.bootstrapLock = bootstrapLock;
        this.clock = clock;
    }

    @Transactional
    public AdminBootstrapResult resolveOrBootstrap(AdminIdentityClaims claims) {
        Objects.requireNonNull(claims, "claims");
        bootstrapLock.acquire();

        return repository.findByOidcSubject(claims.oidcSubject())
                .map(this::resolveExisting)
                .orElseGet(() -> provision(claims));
    }

    private AdminBootstrapResult resolveExisting(AdminAccount account) {
        if (!account.canReceiveAdminSession()) {
            throw new AdminAccessDeniedException(AdminAccessDenialReason.DISABLED_ACCOUNT);
        }
        AdminAccount updated = account.recordSuccessfulLogin(clock.instant());
        return new AdminBootstrapResult(repository.save(updated), AdminBootstrapOutcome.EXISTING_ACCOUNT);
    }

    private AdminBootstrapResult provision(AdminIdentityClaims claims) {
        repository.findByNormalizedEmail(NormalizedEmailAddress.from(claims.email()))
                .ifPresent(account -> {
                    if (!account.oidcSubject().equals(claims.oidcSubject())) {
                        throw new AdminAccessDeniedException(AdminAccessDenialReason.EMAIL_ALREADY_BOUND);
                    }
                });

        boolean anyAdminAccountExists = repository.countAll() > 0;
        if (!anyAdminAccountExists) {
            accessPolicy.evaluateInitialOwnerBootstrap(claims, false).throwIfDenied();
            return createAndRecordLogin(claims, AdminRole.OWNER, AdminBootstrapOutcome.INITIAL_OWNER_BOOTSTRAPPED);
        }

        accessPolicy.evaluateAutomaticProvisioning(claims, true).throwIfDenied();
        return createAndRecordLogin(claims, AdminRole.EDITOR, AdminBootstrapOutcome.AUTOMATICALLY_PROVISIONED);
    }

    private AdminBootstrapResult createAndRecordLogin(
            AdminIdentityClaims claims,
            AdminRole role,
            AdminBootstrapOutcome outcome) {
        Instant now = clock.instant();
        AdminAccount created = AdminAccount.create(
                AdminAccountId.newId(),
                claims.oidcSubject(),
                claims.email(),
                claims.displayName(),
                Set.of(role),
                now);
        repository.save(created);
        AdminAccount loggedIn = repository.save(created.recordSuccessfulLogin(now));
        return new AdminBootstrapResult(loggedIn, outcome);
    }
}
