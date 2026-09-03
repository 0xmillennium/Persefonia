package dev.persefonia.app.contentpublishing.application;

import dev.persefonia.app.audit.integration.ContentPublishingAuditMapper;
import dev.persefonia.audit.application.port.AppendAuditRecordPort;
import dev.persefonia.contentpublishing.application.command.AssignContentTagsCommand;
import dev.persefonia.contentpublishing.application.service.ContentTagAssignmentGateway;
import dev.persefonia.contentpublishing.application.service.ContentTagAssignmentService;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TransactionalContentTagAssignmentGateway implements ContentTagAssignmentGateway {
    private final ContentTagAssignmentService service;
    private final AppendAuditRecordPort audit;
    private final ContentPublishingAuditMapper auditMapper;

    public TransactionalContentTagAssignmentGateway(
            ContentTagAssignmentService service,
            AppendAuditRecordPort audit,
            ContentPublishingAuditMapper auditMapper) {
        this.service = Objects.requireNonNull(service, "service");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.auditMapper = Objects.requireNonNull(auditMapper, "auditMapper");
    }

    @Override
    @Transactional
    public void assign(AssignContentTagsCommand command) {
        service.assign(command);
        audit.append(auditMapper.tagsReplaced(command));
    }
}
