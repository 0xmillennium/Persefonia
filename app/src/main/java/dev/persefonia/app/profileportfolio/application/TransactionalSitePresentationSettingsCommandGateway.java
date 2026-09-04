package dev.persefonia.app.profileportfolio.application;

import dev.persefonia.app.audit.integration.ProfilePortfolioAuditMapper;
import dev.persefonia.audit.application.port.AppendAuditRecordPort;
import dev.persefonia.app.platformoperations.cache.integration.PublicCacheInvalidationRegistrar;
import dev.persefonia.app.platformoperations.cache.integration.PublicCacheInvalidationSignal;
import dev.persefonia.profileportfolio.application.command.SitePresentationSettingsUpdateResult;
import dev.persefonia.profileportfolio.application.command.UpdateSitePresentationSettingsCommand;
import dev.persefonia.profileportfolio.application.service.SitePresentationSettingsCommandGateway;
import dev.persefonia.profileportfolio.application.service.SitePresentationSettingsCommandService;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class TransactionalSitePresentationSettingsCommandGateway
        implements SitePresentationSettingsCommandGateway {
    private final SitePresentationSettingsCommandService service;
    private final AppendAuditRecordPort audit;
    private final ProfilePortfolioAuditMapper auditMapper;
    private final PublicCacheInvalidationRegistrar cacheInvalidation;

    public TransactionalSitePresentationSettingsCommandGateway(
            SitePresentationSettingsCommandService service,
            AppendAuditRecordPort audit,
            ProfilePortfolioAuditMapper auditMapper) {
        this(service, audit, auditMapper, PublicCacheInvalidationRegistrar.noOp());
    }

    @Autowired
    public TransactionalSitePresentationSettingsCommandGateway(
            SitePresentationSettingsCommandService service,
            AppendAuditRecordPort audit,
            ProfilePortfolioAuditMapper auditMapper,
            PublicCacheInvalidationRegistrar cacheInvalidation) {
        this.service = Objects.requireNonNull(service, "service");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.auditMapper = Objects.requireNonNull(auditMapper, "auditMapper");
        this.cacheInvalidation = Objects.requireNonNull(cacheInvalidation, "cacheInvalidation");
    }

    @Override
    @Transactional
    public SitePresentationSettingsUpdateResult update(UpdateSitePresentationSettingsCommand command) {
        SitePresentationSettingsUpdateResult result = service.update(command);
        audit.append(auditMapper.siteSettingsUpdated(command, result));
        cacheInvalidation.register(new PublicCacheInvalidationSignal.SiteSettingsChanged(result.id()));
        return result;
    }
}
