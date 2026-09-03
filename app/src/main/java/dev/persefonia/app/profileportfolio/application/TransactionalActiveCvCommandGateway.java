package dev.persefonia.app.profileportfolio.application;

import dev.persefonia.app.audit.integration.ProfilePortfolioAuditMapper;
import dev.persefonia.audit.application.port.AppendAuditRecordPort;
import dev.persefonia.profileportfolio.application.command.ActiveCvUpdateResult;
import dev.persefonia.profileportfolio.application.command.UpdateActiveCvCommand;
import dev.persefonia.profileportfolio.application.service.ActiveCvCommandGateway;
import dev.persefonia.profileportfolio.application.service.ActiveCvCommandService;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnBean(ActiveCvCommandService.class)
public class TransactionalActiveCvCommandGateway implements ActiveCvCommandGateway {
    private final ActiveCvCommandService service;
    private final AppendAuditRecordPort audit;
    private final ProfilePortfolioAuditMapper auditMapper;

    public TransactionalActiveCvCommandGateway(
            ActiveCvCommandService service,
            AppendAuditRecordPort audit,
            ProfilePortfolioAuditMapper auditMapper) {
        this.service = Objects.requireNonNull(service, "service");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.auditMapper = Objects.requireNonNull(auditMapper, "auditMapper");
    }

    @Override
    @Transactional
    public ActiveCvUpdateResult update(UpdateActiveCvCommand command) {
        ActiveCvUpdateResult result = service.update(command);
        if (result.updated()) {
            audit.append(auditMapper.activeCvUpdated(command, result));
        }
        return result;
    }
}
