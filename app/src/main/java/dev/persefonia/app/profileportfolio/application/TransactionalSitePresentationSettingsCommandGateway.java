package dev.persefonia.app.profileportfolio.application;

import dev.persefonia.app.audit.integration.ProfilePortfolioAuditMapper;
import dev.persefonia.audit.application.port.AppendAuditRecordPort;
import dev.persefonia.profileportfolio.application.command.SitePresentationSettingsUpdateResult;
import dev.persefonia.profileportfolio.application.command.UpdateSitePresentationSettingsCommand;
import dev.persefonia.profileportfolio.application.service.SitePresentationSettingsCommandGateway;
import dev.persefonia.profileportfolio.application.service.SitePresentationSettingsCommandService;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TransactionalSitePresentationSettingsCommandGateway
        implements SitePresentationSettingsCommandGateway {
    private final SitePresentationSettingsCommandService service;
    private final AppendAuditRecordPort audit;
    private final ProfilePortfolioAuditMapper auditMapper;

    public TransactionalSitePresentationSettingsCommandGateway(
            SitePresentationSettingsCommandService service,
            AppendAuditRecordPort audit,
            ProfilePortfolioAuditMapper auditMapper) {
        this.service = Objects.requireNonNull(service, "service");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.auditMapper = Objects.requireNonNull(auditMapper, "auditMapper");
    }

    @Override
    @Transactional
    public SitePresentationSettingsUpdateResult update(UpdateSitePresentationSettingsCommand command) {
        SitePresentationSettingsUpdateResult result = service.update(command);
        audit.append(auditMapper.siteSettingsUpdated(command, result));
        return result;
    }
}
