package dev.persefonia.app.discovery.application;

import dev.persefonia.app.audit.integration.DiscoveryAuditMapper;
import dev.persefonia.audit.application.port.AppendAuditRecordPort;
import dev.persefonia.app.platformoperations.cache.integration.PublicCacheInvalidationRegistrar;
import dev.persefonia.app.platformoperations.cache.integration.PublicCacheInvalidationSignal;
import dev.persefonia.discovery.application.redirect.CreateManualRedirectCommand;
import dev.persefonia.discovery.application.redirect.DeactivateManualRedirectCommand;
import dev.persefonia.discovery.application.redirect.DeactivateRedirectRuleResult;
import dev.persefonia.discovery.application.redirect.RedirectRuleCreationResult;
import dev.persefonia.discovery.application.service.AdminRedirectCommandGateway;
import dev.persefonia.discovery.application.service.AdminRedirectCommandService;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class TransactionalAdminRedirectCommandGateway implements AdminRedirectCommandGateway {
    private final AdminRedirectCommandService service;
    private final AppendAuditRecordPort audit;
    private final DiscoveryAuditMapper auditMapper;
    private final PublicCacheInvalidationRegistrar cacheInvalidation;

    public TransactionalAdminRedirectCommandGateway(
            AdminRedirectCommandService service,
            AppendAuditRecordPort audit,
            DiscoveryAuditMapper auditMapper) {
        this(service, audit, auditMapper, PublicCacheInvalidationRegistrar.noOp());
    }

    @Autowired
    public TransactionalAdminRedirectCommandGateway(
            AdminRedirectCommandService service,
            AppendAuditRecordPort audit,
            DiscoveryAuditMapper auditMapper,
            PublicCacheInvalidationRegistrar cacheInvalidation) {
        this.service = Objects.requireNonNull(service, "service");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.auditMapper = Objects.requireNonNull(auditMapper, "auditMapper");
        this.cacheInvalidation = Objects.requireNonNull(cacheInvalidation, "cacheInvalidation");
    }

    @Override
    @Transactional
    public RedirectRuleCreationResult create(CreateManualRedirectCommand command) {
        RedirectRuleCreationResult result = service.create(command);
        if (result instanceof RedirectRuleCreationResult.Created created) {
            audit.append(auditMapper.created(command, created.redirect()));
            cacheInvalidation.register(new PublicCacheInvalidationSignal.RedirectChanged(
                    created.redirect().redirectRuleId().value(), created.redirect().sourceUrl()));
        }
        return result;
    }

    @Override
    @Transactional
    public DeactivateRedirectRuleResult deactivate(DeactivateManualRedirectCommand command) {
        DeactivateRedirectRuleResult result = service.deactivate(command);
        if (result instanceof DeactivateRedirectRuleResult.Deactivated deactivated) {
            audit.append(auditMapper.deactivated(command, deactivated.redirect()));
            cacheInvalidation.register(new PublicCacheInvalidationSignal.RedirectChanged(
                    deactivated.redirect().redirectRuleId().value(), deactivated.redirect().sourceUrl()));
        }
        return result;
    }
}
