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
import java.util.Objects;

public final class ContentCommandService {
    private final ContentDraftCommandHandler drafts;
    private final ContentPreviewQueryHandler previews;
    private final ContentPublishCommandHandler publishing;
    private final ContentLifecycleCommandHandler lifecycle;

    public ContentCommandService(
            ContentDraftCommandHandler drafts,
            ContentPreviewQueryHandler previews,
            ContentPublishCommandHandler publishing,
            ContentLifecycleCommandHandler lifecycle) {
        this.drafts = Objects.requireNonNull(drafts, "drafts");
        this.previews = Objects.requireNonNull(previews, "previews");
        this.publishing = Objects.requireNonNull(publishing, "publishing");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
    }

    public ContentDraftResult createDraft(CreateContentDraftCommand command) {
        return drafts.create(command);
    }

    public ContentDraftResult updateDraft(UpdateContentDraftCommand command) {
        return drafts.update(command);
    }

    public ContentPreviewResult previewContent(PreviewContentCommand command) {
        return previews.preview(command);
    }

    public ContentPublishResult publishContent(PublishContentCommand command) {
        return publishing.publish(command);
    }

    public ContentUnpublishResult unpublishContent(UnpublishContentCommand command) {
        return lifecycle.unpublish(command);
    }

    public ContentArchiveResult archiveContent(ArchiveContentCommand command) {
        return lifecycle.archive(command);
    }
}
