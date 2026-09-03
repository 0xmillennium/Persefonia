package dev.persefonia.app.communication.application;

import dev.persefonia.app.audit.integration.CommunicationAuditMapper;
import dev.persefonia.audit.application.port.AppendAuditRecordPort;
import dev.persefonia.communication.application.command.ContactMessageStatusCommandGateway;
import dev.persefonia.communication.application.command.UpdateContactMessageStatusCommand;
import dev.persefonia.communication.application.command.UpdateContactMessageStatusCommandService;
import dev.persefonia.communication.application.command.UpdateContactMessageStatusResult;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TransactionalContactMessageStatusCommandGateway implements ContactMessageStatusCommandGateway {
    private final UpdateContactMessageStatusCommandService service;
    private final AppendAuditRecordPort audit;
    private final CommunicationAuditMapper auditMapper;

    public TransactionalContactMessageStatusCommandGateway(
            UpdateContactMessageStatusCommandService service,
            AppendAuditRecordPort audit,
            CommunicationAuditMapper auditMapper) {
        this.service = Objects.requireNonNull(service, "service");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.auditMapper = Objects.requireNonNull(auditMapper, "auditMapper");
    }

    @Override
    @Transactional
    public UpdateContactMessageStatusResult update(UpdateContactMessageStatusCommand command) {
        UpdateContactMessageStatusResult result = service.update(command);
        if (result instanceof UpdateContactMessageStatusResult.Updated updated) {
            audit.append(auditMapper.statusChanged(command, updated));
        }
        return result;
    }
}
