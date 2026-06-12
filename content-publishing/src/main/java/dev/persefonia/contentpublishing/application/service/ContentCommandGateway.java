package dev.persefonia.contentpublishing.application.service;

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

public interface ContentCommandGateway {
    ContentDraftResult createDraft(CreateContentDraftCommand command);

    ContentDraftResult updateDraft(UpdateContentDraftCommand command);

    ContentPreviewResult previewContent(PreviewContentCommand command);

    ContentPublishResult publishContent(PublishContentCommand command);

    ContentUnpublishResult unpublishContent(UnpublishContentCommand command);

    ContentArchiveResult archiveContent(ArchiveContentCommand command);
}
