package dev.persefonia.app.identityaccess.session;

import java.util.Objects;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.persefonia.identityaccess.application.admin.session.AdminSessionRevalidationUseCase;
import dev.persefonia.identityaccess.domain.admin.AdminAccount;
import dev.persefonia.identityaccess.domain.admin.access.AdminIdentityClaims;

@Component
@Lazy
public class TransactionalAdminSessionRevalidationGateway {
    private final AdminSessionRevalidationUseCase useCase;

    public TransactionalAdminSessionRevalidationGateway(AdminSessionRevalidationUseCase useCase) {
        this.useCase = Objects.requireNonNull(useCase, "useCase");
    }

    @Transactional(readOnly = true)
    public AdminAccount revalidate(AdminIdentityClaims claims) {
        return useCase.revalidate(claims);
    }
}
