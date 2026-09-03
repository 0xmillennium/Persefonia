package dev.persefonia.app.audit.integration;

import static dev.persefonia.app.audit.integration.AdminAuditCommandFactory.metadata;

import dev.persefonia.audit.application.command.AppendAuditMetadataCommand;
import dev.persefonia.audit.application.command.AppendAuditRecordCommand;
import dev.persefonia.contentpublishing.application.command.AddSeriesEntryCommand;
import dev.persefonia.contentpublishing.application.command.AddTranslationEntryCommand;
import dev.persefonia.contentpublishing.application.command.ArchiveContentCommand;
import dev.persefonia.contentpublishing.application.command.ArchiveSeriesCommand;
import dev.persefonia.contentpublishing.application.command.AssignContentTagsCommand;
import dev.persefonia.contentpublishing.application.command.ContentArchiveResult;
import dev.persefonia.contentpublishing.application.command.ContentDraftResult;
import dev.persefonia.contentpublishing.application.command.ContentPublishResult;
import dev.persefonia.contentpublishing.application.command.ContentUnpublishResult;
import dev.persefonia.contentpublishing.application.command.CreateContentDraftCommand;
import dev.persefonia.contentpublishing.application.command.CreateSeriesCommand;
import dev.persefonia.contentpublishing.application.command.CreateTranslationGroupCommand;
import dev.persefonia.contentpublishing.application.command.PublishContentCommand;
import dev.persefonia.contentpublishing.application.command.RemoveSeriesEntryCommand;
import dev.persefonia.contentpublishing.application.command.RemoveTranslationEntryCommand;
import dev.persefonia.contentpublishing.application.command.ReorderSeriesEntriesCommand;
import dev.persefonia.contentpublishing.application.command.SeriesResult;
import dev.persefonia.contentpublishing.application.command.TranslationGroupResult;
import dev.persefonia.contentpublishing.application.command.UnpublishContentCommand;
import dev.persefonia.contentpublishing.application.command.UpdateContentDraftCommand;
import dev.persefonia.contentpublishing.application.command.UpdateSeriesCommand;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class ContentPublishingAuditMapper {
    private final AdminAuditCommandFactory factory;

    public ContentPublishingAuditMapper(AdminAuditCommandFactory factory) {
        this.factory = Objects.requireNonNull(factory, "factory");
    }

    public AppendAuditRecordCommand draftCreated(CreateContentDraftCommand command, ContentDraftResult result) {
        return factory.admin(
                AuditActionCatalog.CONTENT_DRAFT_CREATED,
                command.actor().identityRef().value(),
                AuditEntityCatalog.CONTENT_ITEM,
                result.contentId().value(),
                List.of(),
                List.of(
                        metadata("content_type", command.type()),
                        metadata("status", result.status()),
                        metadata("visibility", result.visibility()),
                        metadata("language", result.language())));
    }

    public AppendAuditRecordCommand draftUpdated(UpdateContentDraftCommand command, ContentDraftResult result) {
        List<AppendAuditMetadataCommand> values = new ArrayList<>();
        addChanged(values, "slug_changed", command.slug().specified());
        addChanged(values, "title_changed", command.title().specified());
        addChanged(values, "summary_changed", command.summary().specified());
        addChanged(values, "markdown_changed", command.markdownSource().specified());
        addChanged(values, "metadata_changed", command.metadata().specified());
        addChanged(values, "visibility_changed", command.visibility().specified());
        if (command.visibility().specified()) {
            values.add(metadata("visibility", result.visibility()));
        }
        return admin(
                AuditActionCatalog.CONTENT_DRAFT_UPDATED,
                command.actor().identityRef().value(),
                result.contentId().value(),
                values);
    }

    public AppendAuditRecordCommand published(PublishContentCommand command, ContentPublishResult result) {
        return admin(
                AuditActionCatalog.CONTENT_PUBLISHED,
                command.actor().identityRef().value(),
                result.contentId().value(),
                List.of(metadata("status", result.status()), metadata("revision_number", result.revisionNumber().value())));
    }

    public AppendAuditRecordCommand unpublished(UnpublishContentCommand command, ContentUnpublishResult result) {
        return admin(
                AuditActionCatalog.CONTENT_UNPUBLISHED,
                command.actor().identityRef().value(),
                result.contentId().value(),
                List.of(metadata("status", result.status())));
    }

    public AppendAuditRecordCommand archived(ArchiveContentCommand command, ContentArchiveResult result) {
        return admin(
                AuditActionCatalog.CONTENT_ARCHIVED,
                command.actor().identityRef().value(),
                result.contentId().value(),
                List.of(metadata("status", result.status())));
    }

    public AppendAuditRecordCommand tagsReplaced(AssignContentTagsCommand command) {
        long distinctCount = command.requestedTagIds().stream().distinct().count();
        return admin(
                AuditActionCatalog.CONTENT_TAGS_REPLACED,
                command.actor().identityRef().value(),
                command.contentId().value(),
                List.of(metadata("tag_count", distinctCount)));
    }

    public AppendAuditRecordCommand seriesCreated(CreateSeriesCommand command, SeriesResult result) {
        return factory.admin(
                AuditActionCatalog.SERIES_CREATED,
                command.actor().identityRef().value(),
                AuditEntityCatalog.SERIES,
                result.seriesId().value(),
                List.of(),
                List.of(metadata("language", command.language())));
    }

    public AppendAuditRecordCommand seriesUpdated(UpdateSeriesCommand command, SeriesResult result) {
        return series(AuditActionCatalog.SERIES_UPDATED, command.actor().identityRef().value(), result, List.of());
    }

    public AppendAuditRecordCommand seriesArchived(ArchiveSeriesCommand command, SeriesResult result) {
        return series(
                AuditActionCatalog.SERIES_ARCHIVED,
                command.actor().identityRef().value(),
                result,
                List.of(metadata("status", "ARCHIVED")));
    }

    public AppendAuditRecordCommand seriesEntryAdded(AddSeriesEntryCommand command, SeriesResult result) {
        return series(
                AuditActionCatalog.SERIES_ENTRY_ADDED,
                command.actor().identityRef().value(),
                result,
                List.of(metadata("content_item_id", command.contentItemId().value())));
    }

    public AppendAuditRecordCommand seriesEntryRemoved(RemoveSeriesEntryCommand command, SeriesResult result) {
        return series(
                AuditActionCatalog.SERIES_ENTRY_REMOVED,
                command.actor().identityRef().value(),
                result,
                List.of(metadata("content_item_id", Objects.requireNonNull(result.contentItemId()).value())));
    }

    public AppendAuditRecordCommand seriesEntriesReordered(
            ReorderSeriesEntriesCommand command, SeriesResult result) {
        return series(
                AuditActionCatalog.SERIES_ENTRIES_REORDERED,
                command.actor().identityRef().value(),
                result,
                List.of(metadata("entry_count", command.orderedEntryIds().size())));
    }

    public AppendAuditRecordCommand translationGroupCreated(
            CreateTranslationGroupCommand command, TranslationGroupResult result) {
        return translation(
                AuditActionCatalog.TRANSLATION_GROUP_CREATED,
                command.actor().identityRef().value(),
                result,
                metadata("initial_content_item_id", command.initialContentItemId().value()));
    }

    public AppendAuditRecordCommand translationGroupEntryAdded(
            AddTranslationEntryCommand command, TranslationGroupResult result) {
        return translation(
                AuditActionCatalog.TRANSLATION_GROUP_ENTRY_ADDED,
                command.actor().identityRef().value(),
                result,
                metadata("content_item_id", command.contentItemId().value()));
    }

    public AppendAuditRecordCommand translationGroupEntryRemoved(
            RemoveTranslationEntryCommand command, TranslationGroupResult result) {
        return factory.admin(
                AuditActionCatalog.TRANSLATION_GROUP_ENTRY_REMOVED,
                command.actor().identityRef().value(),
                AuditEntityCatalog.TRANSLATION_GROUP,
                result.translationGroupId().value(),
                List.of(),
                List.of(metadata(
                        "content_item_id", Objects.requireNonNull(result.contentItemId()).value())));
    }

    private AppendAuditRecordCommand admin(
            String action, java.util.UUID actorId, java.util.UUID contentId, List<AppendAuditMetadataCommand> metadata) {
        return factory.admin(action, actorId, AuditEntityCatalog.CONTENT_ITEM, contentId, List.of(), metadata);
    }

    private AppendAuditRecordCommand series(
            String action, java.util.UUID actorId, SeriesResult result, List<AppendAuditMetadataCommand> metadata) {
        return factory.admin(
                action, actorId, AuditEntityCatalog.SERIES, result.seriesId().value(), List.of(), metadata);
    }

    private AppendAuditRecordCommand translation(
            String action,
            java.util.UUID actorId,
            TranslationGroupResult result,
            AppendAuditMetadataCommand metadata) {
        return factory.admin(
                action,
                actorId,
                AuditEntityCatalog.TRANSLATION_GROUP,
                result.translationGroupId().value(),
                List.of(),
                List.of(metadata));
    }

    private static void addChanged(List<AppendAuditMetadataCommand> values, String key, boolean changed) {
        if (changed) {
            values.add(metadata(key, true));
        }
    }
}
