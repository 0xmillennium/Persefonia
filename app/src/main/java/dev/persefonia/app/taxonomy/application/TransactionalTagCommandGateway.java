package dev.persefonia.app.taxonomy.application;

import dev.persefonia.app.audit.integration.TaxonomyAuditMapper;
import dev.persefonia.audit.application.port.AppendAuditRecordPort;
import dev.persefonia.taxonomy.application.command.ArchiveTagCommand;
import dev.persefonia.taxonomy.application.command.CreateTagCommand;
import dev.persefonia.taxonomy.application.command.TagCommandResult;
import dev.persefonia.taxonomy.application.command.UpdateTagCommand;
import dev.persefonia.taxonomy.application.service.TagCommandGateway;
import dev.persefonia.taxonomy.application.service.TagCommandService;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TransactionalTagCommandGateway implements TagCommandGateway {
    private final TagCommandService service;
    private final AppendAuditRecordPort audit;
    private final TaxonomyAuditMapper auditMapper;

    public TransactionalTagCommandGateway(
            TagCommandService service,
            AppendAuditRecordPort audit,
            TaxonomyAuditMapper auditMapper) {
        this.service = Objects.requireNonNull(service, "service");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.auditMapper = Objects.requireNonNull(auditMapper, "auditMapper");
    }

    @Override
    @Transactional
    public TagCommandResult create(CreateTagCommand command) {
        TagCommandResult result = service.create(command);
        audit.append(auditMapper.created(command, result));
        return result;
    }

    @Override
    @Transactional
    public TagCommandResult update(UpdateTagCommand command) {
        TagCommandResult result = service.update(command);
        audit.append(auditMapper.updated(command, result));
        return result;
    }

    @Override
    @Transactional
    public TagCommandResult archive(ArchiveTagCommand command) {
        TagCommandResult result = service.archive(command);
        if (result.mutated()) {
            audit.append(auditMapper.archived(command, result));
        }
        return result;
    }
}
