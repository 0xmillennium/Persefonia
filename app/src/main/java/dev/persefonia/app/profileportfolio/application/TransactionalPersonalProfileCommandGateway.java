package dev.persefonia.app.profileportfolio.application;

import dev.persefonia.app.audit.integration.ProfilePortfolioAuditMapper;
import dev.persefonia.audit.application.port.AppendAuditRecordPort;
import dev.persefonia.profileportfolio.application.command.PersonalProfileUpdateResult;
import dev.persefonia.profileportfolio.application.command.UpsertActivePersonalProfileCommand;
import dev.persefonia.profileportfolio.application.service.PersonalProfileCommandGateway;
import dev.persefonia.profileportfolio.application.service.PersonalProfileCommandService;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TransactionalPersonalProfileCommandGateway implements PersonalProfileCommandGateway {
    private final PersonalProfileCommandService service;
    private final AppendAuditRecordPort audit;
    private final ProfilePortfolioAuditMapper auditMapper;

    public TransactionalPersonalProfileCommandGateway(
            PersonalProfileCommandService service,
            AppendAuditRecordPort audit,
            ProfilePortfolioAuditMapper auditMapper) {
        this.service = Objects.requireNonNull(service, "service");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.auditMapper = Objects.requireNonNull(auditMapper, "auditMapper");
    }

    @Override
    @Transactional
    public PersonalProfileUpdateResult upsertActive(UpsertActivePersonalProfileCommand command) {
        PersonalProfileUpdateResult result = service.upsertActive(command);
        audit.append(auditMapper.profileUpserted(command, result));
        return result;
    }
}
