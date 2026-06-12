package dev.persefonia.contentpublishing.application.service;

import static dev.persefonia.contentpublishing.application.service.ContentApplicationSupport.requiredContent;

import dev.persefonia.contentpublishing.application.authorization.ContentCommandActor;
import dev.persefonia.contentpublishing.application.authorization.ContentCommandAuthorizationPolicy;
import dev.persefonia.contentpublishing.application.exception.ContentCommandRejectedException;
import dev.persefonia.contentpublishing.application.query.AdminContentEditResult;
import dev.persefonia.contentpublishing.application.query.AdminContentListItem;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.content.ContentStatus;
import dev.persefonia.contentpublishing.domain.content.port.ContentItemRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public final class ContentAdminQueryService {
    private final ContentItemRepository contentItems;
    private final ContentCommandAuthorizationPolicy authorization;

    public ContentAdminQueryService(
            ContentItemRepository contentItems,
            ContentCommandAuthorizationPolicy authorization) {
        this.contentItems = Objects.requireNonNull(contentItems, "contentItems");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
    }

    public List<AdminContentListItem> listEditableContent(ContentCommandActor actor) {
        authorization.requireOwner(actor, "content.admin-list");
        return Stream.concat(
                        contentItems.findByStatus(ContentStatus.DRAFT).stream(),
                        contentItems.findByStatus(ContentStatus.UNPUBLISHED).stream())
                .sorted(Comparator.comparing(ContentItem::updatedAt).reversed())
                .map(ContentAdminQueryService::listItem)
                .toList();
    }

    public AdminContentEditResult getContentForEditing(ContentCommandActor actor, ContentId contentId) {
        authorization.requireOwner(actor, "content.admin-edit");
        ContentItem item = requiredContent(contentItems, contentId);
        if (!item.isDraft() && !item.isUnpublished()) {
            throw new ContentCommandRejectedException("Only draft or unpublished content can be edited");
        }
        return editResult(item);
    }

    public AdminContentEditResult getContentForAdmin(ContentCommandActor actor, ContentId contentId) {
        authorization.requireOwner(actor, "content.admin-view");
        return editResult(requiredContent(contentItems, contentId));
    }

    private static AdminContentListItem listItem(ContentItem item) {
        return new AdminContentListItem(
                item.id(),
                item.type(),
                item.language(),
                item.status(),
                item.visibility(),
                item.slug().map(value -> value.value()),
                item.title().map(value -> value.value()),
                item.markdownSource().isPresent(),
                item.updatedAt());
    }

    private static AdminContentEditResult editResult(ContentItem item) {
        var metadata = item.metadata();
        return new AdminContentEditResult(
                item.id(),
                item.type(),
                item.language(),
                item.status(),
                item.visibility(),
                item.slug().map(value -> value.value()),
                item.title().map(value -> value.value()),
                item.summary().map(value -> value.value()),
                item.markdownSource().map(value -> value.value()),
                metadata.seoTitle().map(value -> value.value()),
                metadata.seoDescription().map(value -> value.value()),
                metadata.canonicalPath().map(value -> value.value()),
                metadata.openGraphTitle().map(value -> value.value()),
                metadata.openGraphDescription().map(value -> value.value()),
                metadata.ogImageAssetId().map(value -> value.value().toString()),
                item.updatedAt(),
                item.version());
    }
}
