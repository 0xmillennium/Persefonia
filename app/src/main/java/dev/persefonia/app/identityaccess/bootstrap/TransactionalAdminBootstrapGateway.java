package dev.persefonia.app.identityaccess.bootstrap;

import dev.persefonia.app.audit.integration.IdentityAccessAuditMapper;
import dev.persefonia.audit.application.port.AppendAuditRecordPort;
import dev.persefonia.identityaccess.application.admin.bootstrap.AdminBootstrapOutcome;
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
    private final AppendAuditRecordPort audit;
    private final IdentityAccessAuditMapper auditMapper;

    public TransactionalAdminBootstrapGateway(
            AdminBootstrapUseCase useCase,
            AppendAuditRecordPort audit,
            IdentityAccessAuditMapper auditMapper) {
        this.useCase = Objects.requireNonNull(useCase, "useCase");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.auditMapper = Objects.requireNonNull(auditMapper, "auditMapper");
    }

    @Transactional
    public AdminBootstrapResult resolveOrBootstrap(AdminIdentityClaims claims) {
        AdminBootstrapResult result = useCase.resolveOrBootstrap(claims);
        if (result.outcome() != AdminBootstrapOutcome.EXISTING_ACCOUNT) {
            audit.append(auditMapper.bootstrapped(result));
        }
        return result;
    }
}
