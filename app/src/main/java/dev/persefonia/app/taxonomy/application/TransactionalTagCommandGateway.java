package dev.persefonia.app.taxonomy.application;

import dev.persefonia.app.audit.integration.TaxonomyAuditMapper;
import dev.persefonia.audit.application.port.AppendAuditRecordPort;
import dev.persefonia.app.platformoperations.cache.integration.PublicCacheInvalidationRegistrar;
import dev.persefonia.app.platformoperations.cache.integration.PublicCacheInvalidationSignal;
import dev.persefonia.app.platformoperations.cache.integration.PublicCacheInvalidationSignal.TagChange;
import dev.persefonia.taxonomy.application.command.ArchiveTagCommand;
import dev.persefonia.taxonomy.application.command.CreateTagCommand;
import dev.persefonia.taxonomy.application.command.TagCommandResult;
import dev.persefonia.taxonomy.application.command.UpdateTagCommand;
import dev.persefonia.taxonomy.application.service.TagCommandGateway;
import dev.persefonia.taxonomy.application.service.TagCommandService;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class TransactionalTagCommandGateway implements TagCommandGateway {
    private final TagCommandService service;
    private final AppendAuditRecordPort audit;
    private final TaxonomyAuditMapper auditMapper;
    private final PublicCacheInvalidationRegistrar cacheInvalidation;

    public TransactionalTagCommandGateway(
            TagCommandService service,
            AppendAuditRecordPort audit,
            TaxonomyAuditMapper auditMapper) {
        this(service, audit, auditMapper, PublicCacheInvalidationRegistrar.noOp());
    }

    @Autowired
    public TransactionalTagCommandGateway(
            TagCommandService service,
            AppendAuditRecordPort audit,
            TaxonomyAuditMapper auditMapper,
            PublicCacheInvalidationRegistrar cacheInvalidation) {
        this.service = Objects.requireNonNull(service, "service");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.auditMapper = Objects.requireNonNull(auditMapper, "auditMapper");
        this.cacheInvalidation = Objects.requireNonNull(cacheInvalidation, "cacheInvalidation");
    }

    @Override
    @Transactional
    public TagCommandResult create(CreateTagCommand command) {
        TagCommandResult result = service.create(command);
        audit.append(auditMapper.created(command, result));
        cacheInvalidation.register(new PublicCacheInvalidationSignal.TagChanged(TagChange.CREATE, result));
        return result;
    }

    @Override
    @Transactional
    public TagCommandResult update(UpdateTagCommand command) {
        TagCommandResult result = service.update(command);
        audit.append(auditMapper.updated(command, result));
        cacheInvalidation.register(new PublicCacheInvalidationSignal.TagChanged(TagChange.UPDATE, result));
        return result;
    }

    @Override
    @Transactional
    public TagCommandResult archive(ArchiveTagCommand command) {
        TagCommandResult result = service.archive(command);
        if (result.mutated()) {
            audit.append(auditMapper.archived(command, result));
            cacheInvalidation.register(new PublicCacheInvalidationSignal.TagChanged(TagChange.ARCHIVE, result));
        }
        return result;
    }
}
