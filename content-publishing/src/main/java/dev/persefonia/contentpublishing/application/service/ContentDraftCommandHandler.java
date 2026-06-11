package dev.persefonia.contentpublishing.application.service;

import static dev.persefonia.contentpublishing.application.service.ContentApplicationSupport.draftResult;
import static dev.persefonia.contentpublishing.application.service.ContentApplicationSupport.requiredContent;

import dev.persefonia.contentpublishing.application.authorization.ContentCommandAuthorizationPolicy;
import dev.persefonia.contentpublishing.application.command.ContentDraftResult;
import dev.persefonia.contentpublishing.application.command.ContentFieldUpdate;
import dev.persefonia.contentpublishing.application.command.CreateContentDraftCommand;
import dev.persefonia.contentpublishing.application.command.UpdateContentDraftCommand;
import dev.persefonia.contentpublishing.application.event.ContentCreated;
import dev.persefonia.contentpublishing.application.event.ContentDraftUpdated;
import dev.persefonia.contentpublishing.application.event.ContentSlugChanged;
import dev.persefonia.contentpublishing.application.event.ContentVisibilityChanged;
import dev.persefonia.contentpublishing.application.exception.ContentCommandRejectedException;
import dev.persefonia.contentpublishing.application.port.ContentPublishingEventPublisher;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.content.ContentMetadata;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import dev.persefonia.contentpublishing.domain.content.MarkdownSource;
import dev.persefonia.contentpublishing.domain.content.Slug;
import dev.persefonia.contentpublishing.domain.content.Summary;
import dev.persefonia.contentpublishing.domain.content.Title;
import dev.persefonia.contentpublishing.domain.content.port.ContentItemRepository;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

public final class ContentDraftCommandHandler {
    private final ContentItemRepository contentItems;
    private final ContentCommandAuthorizationPolicy authorization;
    private final ContentPublishingEventPublisher events;

    public ContentDraftCommandHandler(
            ContentItemRepository contentItems,
            ContentCommandAuthorizationPolicy authorization,
            ContentPublishingEventPublisher events) {
        this.contentItems = Objects.requireNonNull(contentItems, "contentItems");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.events = Objects.requireNonNull(events, "events");
    }

    public ContentDraftResult create(CreateContentDraftCommand command) {
        authorization.requireOwner(command.actor(), "content.create-draft");
        ContentItem item = ContentItem.createDraft(
                ContentId.newId(), command.type(), command.visibility(), command.language(), command.requestedAt());
        ContentItem saved = contentItems.save(item);
        events.publish(new ContentCreated(
                saved.id(), saved.type(), saved.language(), command.actor().identityRef(), command.requestedAt()));
        return draftResult(saved);
    }

    public ContentDraftResult update(UpdateContentDraftCommand command) {
        authorization.requireOwner(command.actor(), "content.update-draft");
        ContentItem item = requiredContent(contentItems, command.contentId());
        if (!item.isDraft() && !item.isUnpublished()) {
            throw new ContentCommandRejectedException("Only draft or unpublished content can be edited");
        }
        Optional<Slug> oldSlug = item.slug();
        ContentVisibility oldVisibility = item.visibility();
        applyChanges(item, command);
        ContentItem saved = contentItems.save(item);
        var actor = command.actor().identityRef();
        Instant occurredAt = command.requestedAt();
        events.publish(new ContentDraftUpdated(saved.id(), saved.type(), saved.language(), actor, occurredAt));
        if (!oldSlug.equals(saved.slug())) {
            events.publish(new ContentSlugChanged(
                    saved.id(), saved.type(), saved.language(), actor, occurredAt,
                    oldSlug.orElse(null), saved.slug().orElse(null)));
        }
        if (oldVisibility != saved.visibility()) {
            events.publish(new ContentVisibilityChanged(
                    saved.id(), saved.type(), saved.language(), actor, occurredAt, oldVisibility, saved.visibility()));
        }
        return draftResult(saved);
    }

    private void applyChanges(ContentItem item, UpdateContentDraftCommand command) {
        applyNullable(command.slug(), item::changeSlug, item::clearSlug, command.requestedAt());
        applyNullable(command.title(), item::changeTitle, item::clearTitle, command.requestedAt());
        applyNullable(command.summary(), item::changeSummary, item::clearSummary, command.requestedAt());
        applyNullable(command.markdownSource(), item::changeMarkdownSource, item::clearMarkdownSource, command.requestedAt());
        applyRequired(command.metadata(), item::changeMetadata, command.requestedAt());
        applyRequired(command.visibility(), item::changeVisibility, command.requestedAt());
    }

    private <T> void applyNullable(ContentFieldUpdate<T> update, BiConsumer<T, Instant> setter, java.util.function.Consumer<Instant> clearer, Instant now) {
        if (!update.specified()) {
            return;
        }
        if (update.value() == null) {
            clearer.accept(now);
        } else {
            setter.accept(update.value(), now);
        }
    }

    private <T> void applyRequired(ContentFieldUpdate<T> update, BiConsumer<T, Instant> setter, Instant now) {
        if (update.specified()) {
            setter.accept(update.value(), now);
        }
    }
}
