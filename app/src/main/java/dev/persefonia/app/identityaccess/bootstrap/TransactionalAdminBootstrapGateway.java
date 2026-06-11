package dev.persefonia.app.identityaccess.bootstrap;

import java.util.Objects;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.persefonia.identityaccess.application.admin.bootstrap.AdminBootstrapResult;
import dev.persefonia.identityaccess.application.admin.bootstrap.AdminBootstrapUseCase;
import dev.persefonia.identityaccess.domain.admin.access.AdminIdentityClaims;

@Component
@Lazy
public class TransactionalAdminBootstrapGateway {
    private final AdminBootstrapUseCase useCase;

    public TransactionalAdminBootstrapGateway(AdminBootstrapUseCase useCase) {
        this.useCase = Objects.requireNonNull(useCase, "useCase");
    }

    @Transactional
    public AdminBootstrapResult resolveOrBootstrap(AdminIdentityClaims claims) {
        return useCase.resolveOrBootstrap(claims);
    }
}
