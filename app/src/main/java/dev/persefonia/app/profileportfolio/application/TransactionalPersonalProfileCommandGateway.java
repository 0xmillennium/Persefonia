package dev.persefonia.app.profileportfolio.application;

import dev.persefonia.app.audit.integration.ProfilePortfolioAuditMapper;
import dev.persefonia.audit.application.port.AppendAuditRecordPort;
import dev.persefonia.app.platformoperations.cache.integration.PublicCacheInvalidationRegistrar;
import dev.persefonia.app.platformoperations.cache.integration.PublicCacheInvalidationSignal;
import dev.persefonia.profileportfolio.application.command.PersonalProfileUpdateResult;
import dev.persefonia.profileportfolio.application.command.UpsertActivePersonalProfileCommand;
import dev.persefonia.profileportfolio.application.service.PersonalProfileCommandGateway;
import dev.persefonia.profileportfolio.application.service.PersonalProfileCommandService;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class TransactionalPersonalProfileCommandGateway implements PersonalProfileCommandGateway {
    private final PersonalProfileCommandService service;
    private final AppendAuditRecordPort audit;
    private final ProfilePortfolioAuditMapper auditMapper;
    private final PublicCacheInvalidationRegistrar cacheInvalidation;

    public TransactionalPersonalProfileCommandGateway(
            PersonalProfileCommandService service,
            AppendAuditRecordPort audit,
            ProfilePortfolioAuditMapper auditMapper) {
        this(service, audit, auditMapper, PublicCacheInvalidationRegistrar.noOp());
    }

    @Autowired
    public TransactionalPersonalProfileCommandGateway(
            PersonalProfileCommandService service,
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
    public PersonalProfileUpdateResult upsertActive(UpsertActivePersonalProfileCommand command) {
        PersonalProfileUpdateResult result = service.upsertActive(command);
        audit.append(auditMapper.profileUpserted(command, result));
        cacheInvalidation.register(new PublicCacheInvalidationSignal.PersonalProfileChanged(result.profileId()));
        return result;
    }
}
