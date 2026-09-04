package dev.persefonia.app.contentpublishing.application;

import dev.persefonia.app.audit.integration.ContentPublishingAuditMapper;
import dev.persefonia.audit.application.port.AppendAuditRecordPort;
import dev.persefonia.app.platformoperations.cache.integration.PublicCacheInvalidationRegistrar;
import dev.persefonia.app.platformoperations.cache.integration.PublicCacheInvalidationSignal;
import dev.persefonia.contentpublishing.application.command.AssignContentTagsCommand;
import dev.persefonia.contentpublishing.application.service.ContentTagAssignmentGateway;
import dev.persefonia.contentpublishing.application.service.ContentTagAssignmentService;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class TransactionalContentTagAssignmentGateway implements ContentTagAssignmentGateway {
    private final ContentTagAssignmentService service;
    private final AppendAuditRecordPort audit;
    private final ContentPublishingAuditMapper auditMapper;
    private final PublicCacheInvalidationRegistrar cacheInvalidation;

    public TransactionalContentTagAssignmentGateway(
            ContentTagAssignmentService service,
            AppendAuditRecordPort audit,
            ContentPublishingAuditMapper auditMapper) {
        this(service, audit, auditMapper, PublicCacheInvalidationRegistrar.noOp());
    }

    @Autowired
    public TransactionalContentTagAssignmentGateway(
            ContentTagAssignmentService service,
            AppendAuditRecordPort audit,
            ContentPublishingAuditMapper auditMapper,
            PublicCacheInvalidationRegistrar cacheInvalidation) {
        this.service = Objects.requireNonNull(service, "service");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.auditMapper = Objects.requireNonNull(auditMapper, "auditMapper");
        this.cacheInvalidation = Objects.requireNonNull(cacheInvalidation, "cacheInvalidation");
    }

    @Override
    @Transactional
    public void assign(AssignContentTagsCommand command) {
        var facts = service.assignWithFacts(command);
        audit.append(auditMapper.tagsReplaced(command));
        cacheInvalidation.register(new PublicCacheInvalidationSignal.ContentTagsChanged(facts));
    }
}
