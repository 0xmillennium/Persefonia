package dev.persefonia.identityaccess.application.admin.session;

import java.util.Objects;

import dev.persefonia.identityaccess.domain.admin.AdminAccount;
import dev.persefonia.identityaccess.domain.admin.AdminAccountRepository;
import dev.persefonia.identityaccess.domain.admin.access.AdminAccessDeniedException;
import dev.persefonia.identityaccess.domain.admin.access.AdminAccessDenialReason;
import dev.persefonia.identityaccess.domain.admin.access.AdminAccessPolicy;
import dev.persefonia.identityaccess.domain.admin.access.AdminIdentityClaims;

public final class AdminSessionRevalidationUseCase {
    private final AdminAccountRepository repository;
    private final AdminAccessPolicy accessPolicy;

    public AdminSessionRevalidationUseCase(AdminAccountRepository repository, AdminAccessPolicy accessPolicy) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
    }

    public AdminAccount revalidate(AdminIdentityClaims claims) {
        Objects.requireNonNull(claims, "claims");
        accessPolicy.evaluateAdmission(claims).throwIfDenied();
        AdminAccount account = repository.findByOidcSubject(claims.oidcSubject())
                .orElseThrow(() -> new AdminAccessDeniedException(AdminAccessDenialReason.ADMIN_ACCOUNT_NOT_FOUND));
        if (!account.canReceiveAdminSession()) {
            throw new AdminAccessDeniedException(AdminAccessDenialReason.DISABLED_ACCOUNT);
        }
        return account;
    }
}
