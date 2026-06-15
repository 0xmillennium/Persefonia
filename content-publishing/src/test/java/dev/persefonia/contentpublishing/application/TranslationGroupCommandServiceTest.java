package dev.persefonia.contentpublishing.application;

import static dev.persefonia.contentpublishing.application.support.ContentApplicationFixtures.EDITOR;
import static dev.persefonia.contentpublishing.application.support.ContentApplicationFixtures.NOW;
import static dev.persefonia.contentpublishing.application.support.ContentApplicationFixtures.OWNER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.contentpublishing.application.command.AddTranslationEntryCommand;
import dev.persefonia.contentpublishing.application.command.CreateTranslationGroupCommand;
import dev.persefonia.contentpublishing.application.command.RemoveTranslationEntryCommand;
import dev.persefonia.contentpublishing.application.command.TranslationGroupResult;
import dev.persefonia.contentpublishing.application.exception.ContentNotFoundException;
import dev.persefonia.contentpublishing.application.exception.TranslationGroupCommandRejectedException;
import dev.persefonia.contentpublishing.application.service.TranslationGroupCommandService;
import dev.persefonia.contentpublishing.application.support.InMemoryContentItemRepository;
import dev.persefonia.contentpublishing.application.support.InMemoryTranslationGroupRepository;
import dev.persefonia.contentpublishing.application.support.TestContentAuthorizationPolicy;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import dev.persefonia.contentpublishing.domain.translation.TranslationGroup;
import dev.persefonia.contentpublishing.domain.translation.TranslationGroupEntry;
import dev.persefonia.contentpublishing.domain.translation.TranslationGroupId;
import org.junit.jupiter.api.Test;

class TranslationGroupCommandServiceTest {
    private final InMemoryContentItemRepository contentItems = new InMemoryContentItemRepository();
    private final InMemoryTranslationGroupRepository groups = new InMemoryTranslationGroupRepository();
    private final TestContentAuthorizationPolicy authorization = new TestContentAuthorizationPolicy();
    private final TranslationGroupCommandService service =
            new TranslationGroupCommandService(contentItems, groups, authorization);

    @Test
    void ownerCanCreateTranslationGroup() {
        ContentItem english = content(ContentLanguage.EN, ContentType.ARTICLE);

        TranslationGroupResult result = service.create(new CreateTranslationGroupCommand(OWNER, english.id(), NOW));

        TranslationGroup group = groups.findById(result.translationGroupId()).orElseThrow();
        assertThat(group.entries()).hasSize(1);
        assertThat(group.containsContentItem(english.id())).isTrue();
    }

    @Test
    void nonOwnerCannotCreateTranslationGroup() {
        ContentItem english = content(ContentLanguage.EN, ContentType.ARTICLE);

        assertThatThrownBy(() -> service.create(new CreateTranslationGroupCommand(EDITOR, english.id(), NOW)))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void ownerCanAddTranslationEntry() {
        TranslationGroupId groupId = createGroup(content(ContentLanguage.EN, ContentType.ARTICLE));
        ContentItem turkish = content(ContentLanguage.TR, ContentType.ARTICLE);

        service.addEntry(new AddTranslationEntryCommand(OWNER, groupId, turkish.id(), NOW));

        assertThat(groups.findById(groupId).orElseThrow().entries()).hasSize(2);
    }

    @Test
    void nonOwnerCannotAddTranslationEntry() {
        TranslationGroupId groupId = createGroup(content(ContentLanguage.EN, ContentType.ARTICLE));
        ContentItem turkish = content(ContentLanguage.TR, ContentType.ARTICLE);

        assertThatThrownBy(() -> service.addEntry(new AddTranslationEntryCommand(EDITOR, groupId, turkish.id(), NOW)))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void ownerCanRemoveTranslationEntry() {
        TranslationGroupId groupId = createGroup(content(ContentLanguage.EN, ContentType.ARTICLE));
        ContentItem turkish = content(ContentLanguage.TR, ContentType.ARTICLE);
        service.addEntry(new AddTranslationEntryCommand(OWNER, groupId, turkish.id(), NOW));
        var entryId = entryFor(groupId, turkish.id());

        service.removeEntry(new RemoveTranslationEntryCommand(OWNER, groupId, entryId, NOW));

        assertThat(groups.findById(groupId).orElseThrow().entries()).hasSize(1);
    }

    @Test
    void nonOwnerCannotRemoveTranslationEntry() {
        TranslationGroupId groupId = createGroup(content(ContentLanguage.EN, ContentType.ARTICLE));
        ContentItem turkish = content(ContentLanguage.TR, ContentType.ARTICLE);
        service.addEntry(new AddTranslationEntryCommand(OWNER, groupId, turkish.id(), NOW));
        var entryId = entryFor(groupId, turkish.id());

        assertThatThrownBy(() -> service.removeEntry(
                new RemoveTranslationEntryCommand(EDITOR, groupId, entryId, NOW)))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void missingContentItemRejected() {
        assertThatThrownBy(() -> service.create(new CreateTranslationGroupCommand(OWNER, ContentId.newId(), NOW)))
                .isInstanceOf(ContentNotFoundException.class);
    }

    @Test
    void contentAlreadyInAnotherGroupRejected() {
        ContentItem english = content(ContentLanguage.EN, ContentType.ARTICLE);
        createGroup(english);

        assertThatThrownBy(() -> service.create(new CreateTranslationGroupCommand(OWNER, english.id(), NOW)))
                .isInstanceOfSatisfying(TranslationGroupCommandRejectedException.class, exception ->
                        assertThat(exception.reason())
                                .isEqualTo(TranslationGroupCommandRejectedException.Reason.ALREADY_IN_GROUP));
    }

    @Test
    void duplicateLanguageRejected() {
        TranslationGroupId groupId = createGroup(content(ContentLanguage.EN, ContentType.ARTICLE));
        ContentItem secondEnglish = content(ContentLanguage.EN, ContentType.ARTICLE);

        assertThatThrownBy(() -> service.addEntry(
                new AddTranslationEntryCommand(OWNER, groupId, secondEnglish.id(), NOW)))
                .isInstanceOfSatisfying(TranslationGroupCommandRejectedException.class, exception ->
                        assertThat(exception.reason())
                                .isEqualTo(TranslationGroupCommandRejectedException.Reason.DUPLICATE_LANGUAGE));
    }

    @Test
    void contentLanguageIsLoadedFromContentItem() {
        ContentItem turkish = content(ContentLanguage.TR, ContentType.ARTICLE);

        TranslationGroupResult result = service.create(new CreateTranslationGroupCommand(OWNER, turkish.id(), NOW));

        TranslationGroupEntry entry = groups.findById(result.translationGroupId()).orElseThrow().entries().getFirst();
        assertThat(entry.language()).isEqualTo(ContentLanguage.TR);
    }

    @Test
    void contentTypeIsLoadedFromContentItem() {
        ContentItem note = content(ContentLanguage.EN, ContentType.NOTE);

        TranslationGroupResult result = service.create(new CreateTranslationGroupCommand(OWNER, note.id(), NOW));

        TranslationGroupEntry entry = groups.findById(result.translationGroupId()).orElseThrow().entries().getFirst();
        assertThat(entry.contentType()).isEqualTo(ContentType.NOTE);
    }

    @Test
    void sameContentTypeRequired() {
        TranslationGroupId groupId = createGroup(content(ContentLanguage.EN, ContentType.ARTICLE));
        ContentItem note = content(ContentLanguage.TR, ContentType.NOTE);

        assertThatThrownBy(() -> service.addEntry(new AddTranslationEntryCommand(OWNER, groupId, note.id(), NOW)))
                .isInstanceOfSatisfying(TranslationGroupCommandRejectedException.class, exception ->
                        assertThat(exception.reason())
                                .isEqualTo(TranslationGroupCommandRejectedException.Reason.DIFFERENT_CONTENT_TYPE));
    }

    private ContentItem content(ContentLanguage language, ContentType type) {
        ContentItem item = ContentItem.createDraft(
                ContentId.newId(), type, ContentVisibility.PUBLIC, language, NOW);
        contentItems.add(item);
        return item;
    }

    private TranslationGroupId createGroup(ContentItem item) {
        return service.create(new CreateTranslationGroupCommand(OWNER, item.id(), NOW)).translationGroupId();
    }

    private dev.persefonia.contentpublishing.domain.translation.TranslationGroupEntryId entryFor(
            TranslationGroupId groupId, ContentId contentItemId) {
        return groups.findById(groupId).orElseThrow().entries().stream()
                .filter(entry -> entry.contentItemId().equals(contentItemId))
                .findFirst()
                .orElseThrow()
                .id();
    }
}
