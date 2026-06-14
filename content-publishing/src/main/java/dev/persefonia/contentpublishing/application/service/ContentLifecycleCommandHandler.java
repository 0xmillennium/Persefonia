package dev.persefonia.contentpublishing.application.service;

import static dev.persefonia.contentpublishing.application.service.ContentApplicationSupport.requiredContent;

import dev.persefonia.contentpublishing.application.authorization.ContentCommandAuthorizationPolicy;
import dev.persefonia.contentpublishing.application.command.ArchiveContentCommand;
import dev.persefonia.contentpublishing.application.command.ContentArchiveResult;
import dev.persefonia.contentpublishing.application.command.ContentUnpublishResult;
import dev.persefonia.contentpublishing.application.command.UnpublishContentCommand;
import dev.persefonia.contentpublishing.application.discovery.ContentDiscoverabilityCoordinator;
import dev.persefonia.contentpublishing.application.event.ContentArchived;
import dev.persefonia.contentpublishing.application.event.ContentUnpublished;
import dev.persefonia.contentpublishing.application.port.ContentPublishingEventPublisher;
import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.content.port.ContentItemRepository;
import java.util.Objects;

public final class ContentLifecycleCommandHandler {
    private final ContentItemRepository contentItems;
    private final ContentCommandAuthorizationPolicy authorization;
    private final ContentPublishingEventPublisher events;
    private final ContentDiscoverabilityCoordinator discoverability;

    public ContentLifecycleCommandHandler(
            ContentItemRepository contentItems,
            ContentCommandAuthorizationPolicy authorization,
            ContentPublishingEventPublisher events,
            ContentDiscoverabilityCoordinator discoverability) {
        this.contentItems = Objects.requireNonNull(contentItems, "contentItems");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.events = Objects.requireNonNull(events, "events");
        this.discoverability = Objects.requireNonNull(discoverability, "discoverability");
    }

    public ContentUnpublishResult unpublish(UnpublishContentCommand command) {
        authorization.requireOwner(command.actor(), "content.unpublish");
        ContentItem item = requiredContent(contentItems, command.contentId());
        item.unpublish(command.requestedAt());
        ContentItem saved = contentItems.save(item);
        discoverability.removeContent(saved);
        events.publish(new ContentUnpublished(
                saved.id(), saved.type(), saved.language(), command.actor().identityRef(),
                command.requestedAt(), saved.unpublishedAt().orElseThrow()));
        return new ContentUnpublishResult(saved.id(), saved.status(), saved.unpublishedAt().orElseThrow());
    }

    public ContentArchiveResult archive(ArchiveContentCommand command) {
        authorization.requireOwner(command.actor(), "content.archive");
        ContentItem item = requiredContent(contentItems, command.contentId());
        item.archive(command.requestedAt());
        ContentItem saved = contentItems.save(item);
        discoverability.removeContent(saved);
        events.publish(new ContentArchived(
                saved.id(), saved.type(), saved.language(), command.actor().identityRef(), command.requestedAt()));
        return new ContentArchiveResult(saved.id(), saved.status(), command.requestedAt());
    }
}
