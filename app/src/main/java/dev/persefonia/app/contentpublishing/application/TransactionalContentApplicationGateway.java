package dev.persefonia.app.contentpublishing.application;

import dev.persefonia.app.audit.integration.ContentPublishingAuditMapper;
import dev.persefonia.audit.application.port.AppendAuditRecordPort;
import dev.persefonia.contentpublishing.application.command.ArchiveContentCommand;
import dev.persefonia.contentpublishing.application.command.ContentArchiveResult;
import dev.persefonia.contentpublishing.application.command.ContentDraftResult;
import dev.persefonia.contentpublishing.application.command.ContentPreviewResult;
import dev.persefonia.contentpublishing.application.command.ContentPublishResult;
import dev.persefonia.contentpublishing.application.command.ContentUnpublishResult;
import dev.persefonia.contentpublishing.application.command.CreateContentDraftCommand;
import dev.persefonia.contentpublishing.application.command.PreviewContentCommand;
import dev.persefonia.contentpublishing.application.command.PublishContentCommand;
import dev.persefonia.contentpublishing.application.command.UnpublishContentCommand;
import dev.persefonia.contentpublishing.application.command.UpdateContentDraftCommand;
import dev.persefonia.contentpublishing.application.service.ContentCommandGateway;
import dev.persefonia.contentpublishing.application.service.ContentCommandService;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TransactionalContentApplicationGateway implements ContentCommandGateway {
    private final ContentCommandService service;
    private final AppendAuditRecordPort audit;
    private final ContentPublishingAuditMapper auditMapper;

    public TransactionalContentApplicationGateway(
            ContentCommandService service,
            AppendAuditRecordPort audit,
            ContentPublishingAuditMapper auditMapper) {
        this.service = Objects.requireNonNull(service, "service");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.auditMapper = Objects.requireNonNull(auditMapper, "auditMapper");
    }

    @Override
    @Transactional
    public ContentDraftResult createDraft(CreateContentDraftCommand command) {
        ContentDraftResult result = service.createDraft(command);
        audit.append(auditMapper.draftCreated(command, result));
        return result;
    }

    @Override
    @Transactional
    public ContentDraftResult updateDraft(UpdateContentDraftCommand command) {
        ContentDraftResult result = service.updateDraft(command);
        audit.append(auditMapper.draftUpdated(command, result));
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public ContentPreviewResult previewContent(PreviewContentCommand command) {
        return service.previewContent(command);
    }

    @Override
    @Transactional
    public ContentPublishResult publishContent(PublishContentCommand command) {
        ContentPublishResult result = service.publishContent(command);
        audit.append(auditMapper.published(command, result));
        return result;
    }

    @Override
    @Transactional
    public ContentUnpublishResult unpublishContent(UnpublishContentCommand command) {
        ContentUnpublishResult result = service.unpublishContent(command);
        audit.append(auditMapper.unpublished(command, result));
        return result;
    }

    @Override
    @Transactional
    public ContentArchiveResult archiveContent(ArchiveContentCommand command) {
        ContentArchiveResult result = service.archiveContent(command);
        audit.append(auditMapper.archived(command, result));
        return result;
    }
}
