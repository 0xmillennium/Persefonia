package dev.persefonia.contentpublishing.application.service;

import static dev.persefonia.contentpublishing.application.service.ContentApplicationSupport.requiredContent;

import dev.persefonia.contentpublishing.application.authorization.ContentCommandAuthorizationPolicy;
import dev.persefonia.contentpublishing.application.command.AddTranslationEntryCommand;
import dev.persefonia.contentpublishing.application.command.CreateTranslationGroupCommand;
import dev.persefonia.contentpublishing.application.command.RemoveTranslationEntryCommand;
import dev.persefonia.contentpublishing.application.command.TranslationGroupResult;
import dev.persefonia.contentpublishing.application.exception.TranslationGroupCommandRejectedException;
import dev.persefonia.contentpublishing.application.exception.TranslationGroupNotFoundException;
import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.content.port.ContentItemRepository;
import dev.persefonia.contentpublishing.domain.translation.TranslationGroup;
import dev.persefonia.contentpublishing.domain.translation.TranslationGroupEntry;
import dev.persefonia.contentpublishing.domain.translation.TranslationGroupEntryId;
import dev.persefonia.contentpublishing.domain.translation.TranslationGroupId;
import dev.persefonia.contentpublishing.domain.translation.port.TranslationGroupRepository;
import dev.persefonia.contentpublishing.application.discovery.ContentPublicRouteFactory;
import dev.persefonia.contentpublishing.application.publicview.ContentPublicExposurePolicy;
import java.util.Objects;

public final class TranslationGroupCommandService {
    private final ContentItemRepository contentItems;
    private final TranslationGroupRepository translationGroups;
    private final ContentCommandAuthorizationPolicy authorization;
    private final ContentPublicExposurePolicy exposurePolicy;
    private final ContentPublicRouteFactory publicRoutes;

    public TranslationGroupCommandService(
            ContentItemRepository contentItems,
            TranslationGroupRepository translationGroups,
            ContentCommandAuthorizationPolicy authorization) {
        this(contentItems, translationGroups, authorization,
                new ContentPublicExposurePolicy(), new ContentPublicRouteFactory());
    }

    public TranslationGroupCommandService(
            ContentItemRepository contentItems,
            TranslationGroupRepository translationGroups,
            ContentCommandAuthorizationPolicy authorization,
            ContentPublicExposurePolicy exposurePolicy,
            ContentPublicRouteFactory publicRoutes) {
        this.contentItems = Objects.requireNonNull(contentItems, "contentItems");
        this.translationGroups = Objects.requireNonNull(translationGroups, "translationGroups");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.exposurePolicy = Objects.requireNonNull(exposurePolicy, "exposurePolicy");
        this.publicRoutes = Objects.requireNonNull(publicRoutes, "publicRoutes");
    }

    public TranslationGroupResult create(CreateTranslationGroupCommand command) {
        Objects.requireNonNull(command, "command");
        authorization.requireOwner(command.actor(), "translation-group.create");
        ContentItem item = requiredContent(contentItems, command.initialContentItemId());
        requireNotInAnyGroup(item);

        TranslationGroupEntry entry = entryFor(item, command.createdAt());
        TranslationGroup group = TranslationGroup.create(TranslationGroupId.newId(), entry, command.createdAt());
        TranslationGroup saved = translationGroups.save(group);
        return new TranslationGroupResult(saved.id());
    }

    public TranslationGroupResult addEntry(AddTranslationEntryCommand command) {
        Objects.requireNonNull(command, "command");
        authorization.requireOwner(command.actor(), "translation-group.add-entry");
        TranslationGroup group = requiredGroup(command.translationGroupId());
        ContentItem item = requiredContent(contentItems, command.contentItemId());
        requireNotInAnyGroup(item);

        if (group.containsLanguage(item.language())) {
            throw new TranslationGroupCommandRejectedException(
                    TranslationGroupCommandRejectedException.Reason.DUPLICATE_LANGUAGE,
                    "The translation group already contains a " + item.language() + " entry.");
        }
        if (item.type() != group.contentType()) {
            throw new TranslationGroupCommandRejectedException(
                    TranslationGroupCommandRejectedException.Reason.DIFFERENT_CONTENT_TYPE,
                    "Translation entries must share the same content type.");
        }

        group.addEntry(entryFor(item, command.addedAt()), command.addedAt());
        TranslationGroup saved = translationGroups.save(group);
        return new TranslationGroupResult(saved.id(), item.id(), translationVisible(item), java.util.Optional.empty());
    }

    public TranslationGroupResult removeEntry(RemoveTranslationEntryCommand command) {
        Objects.requireNonNull(command, "command");
        authorization.requireOwner(command.actor(), "translation-group.remove-entry");
        TranslationGroup group = requiredGroup(command.translationGroupId());

        TranslationGroupEntryId entryId = command.entryId();
        var removedEntry = group.entries().stream()
                .filter(entry -> entry.id().equals(entryId))
                .findFirst();
        if (removedEntry.isEmpty()) {
            throw new TranslationGroupCommandRejectedException(
                    TranslationGroupCommandRejectedException.Reason.ENTRY_NOT_FOUND,
                    "The translation entry does not exist in this group.");
        }
        if (group.entries().size() == 1) {
            throw new TranslationGroupCommandRejectedException(
                    TranslationGroupCommandRejectedException.Reason.LAST_ENTRY,
                    "A translation group must keep at least one entry.");
        }

        group.removeEntry(entryId, command.removedAt());
        TranslationGroup saved = translationGroups.save(group);
        ContentItem removedItem = requiredContent(contentItems, removedEntry.orElseThrow().contentItemId());
        boolean publicVisible = translationVisible(removedItem);
        var removedRoute = publicVisible
                ? java.util.Optional.of(publicRoutes.publicUrl(
                        removedItem.type(), removedItem.language(), removedItem.slug().orElseThrow()))
                : java.util.Optional.<dev.persefonia.discovery.application.contract.PublicUrl>empty();
        return new TranslationGroupResult(saved.id(), removedItem.id(), publicVisible, removedRoute);
    }

    private void requireNotInAnyGroup(ContentItem item) {
        if (translationGroups.contentItemBelongsToAnyGroup(item.id())) {
            throw new TranslationGroupCommandRejectedException(
                    TranslationGroupCommandRejectedException.Reason.ALREADY_IN_GROUP,
                    "This content item already belongs to a translation group.");
        }
    }

    private TranslationGroup requiredGroup(TranslationGroupId id) {
        return translationGroups.findById(id).orElseThrow(() -> new TranslationGroupNotFoundException(id));
    }

    private static TranslationGroupEntry entryFor(ContentItem item, java.time.Instant addedAt) {
        return new TranslationGroupEntry(
                TranslationGroupEntryId.newId(),
                item.id(),
                item.language(),
                item.type(),
                addedAt);
    }

    private boolean translationVisible(ContentItem item) {
        var exposure = exposurePolicy.snapshot(item);
        return exposure.listed();
    }
}
