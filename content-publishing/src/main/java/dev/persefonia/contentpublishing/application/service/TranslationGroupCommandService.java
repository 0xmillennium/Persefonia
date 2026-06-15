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
import java.util.Objects;

public final class TranslationGroupCommandService {
    private final ContentItemRepository contentItems;
    private final TranslationGroupRepository translationGroups;
    private final ContentCommandAuthorizationPolicy authorization;

    public TranslationGroupCommandService(
            ContentItemRepository contentItems,
            TranslationGroupRepository translationGroups,
            ContentCommandAuthorizationPolicy authorization) {
        this.contentItems = Objects.requireNonNull(contentItems, "contentItems");
        this.translationGroups = Objects.requireNonNull(translationGroups, "translationGroups");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
    }

    public TranslationGroupResult create(CreateTranslationGroupCommand command) {
        Objects.requireNonNull(command, "command");
        authorization.requireOwner(command.actor(), "translation-group.create");
        ContentItem item = requiredContent(contentItems, command.initialContentItemId());
        requireNotInAnyGroup(item);

        TranslationGroupEntry entry = entryFor(item, command.createdAt());
        TranslationGroup group = TranslationGroup.create(TranslationGroupId.newId(), entry, command.createdAt());
        return new TranslationGroupResult(translationGroups.save(group).id());
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
        return new TranslationGroupResult(translationGroups.save(group).id());
    }

    public TranslationGroupResult removeEntry(RemoveTranslationEntryCommand command) {
        Objects.requireNonNull(command, "command");
        authorization.requireOwner(command.actor(), "translation-group.remove-entry");
        TranslationGroup group = requiredGroup(command.translationGroupId());

        TranslationGroupEntryId entryId = command.entryId();
        boolean present = group.entries().stream().anyMatch(entry -> entry.id().equals(entryId));
        if (!present) {
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
        return new TranslationGroupResult(translationGroups.save(group).id());
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
}
