package dev.persefonia.app.contentpublishing.application;

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
import dev.persefonia.contentpublishing.application.query.ContentRevisionResult;
import dev.persefonia.contentpublishing.application.query.ListContentRevisionsQuery;
import dev.persefonia.contentpublishing.application.service.ContentCommandGateway;
import dev.persefonia.contentpublishing.application.service.ContentCommandService;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TransactionalContentApplicationGateway implements ContentCommandGateway {
    private final ContentCommandService service;

    public TransactionalContentApplicationGateway(ContentCommandService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    @Transactional
    public ContentDraftResult createDraft(CreateContentDraftCommand command) {
        return service.createDraft(command);
    }

    @Override
    @Transactional
    public ContentDraftResult updateDraft(UpdateContentDraftCommand command) {
        return service.updateDraft(command);
    }

    @Override
    @Transactional(readOnly = true)
    public ContentPreviewResult previewContent(PreviewContentCommand command) {
        return service.previewContent(command);
    }

    @Override
    @Transactional
    public ContentPublishResult publishContent(PublishContentCommand command) {
        return service.publishContent(command);
    }

    @Override
    @Transactional
    public ContentUnpublishResult unpublishContent(UnpublishContentCommand command) {
        return service.unpublishContent(command);
    }

    @Override
    @Transactional
    public ContentArchiveResult archiveContent(ArchiveContentCommand command) {
        return service.archiveContent(command);
    }

    @Transactional(readOnly = true)
    public List<ContentRevisionResult> listRevisions(ListContentRevisionsQuery query) {
        return service.listRevisions(query);
    }
}
