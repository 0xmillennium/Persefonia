package dev.persefonia.contentpublishing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.contentpublishing.application.authorization.ContentCommandActor;
import dev.persefonia.contentpublishing.application.command.AssignContentTagsCommand;
import dev.persefonia.contentpublishing.application.exception.ContentNotFoundException;
import dev.persefonia.contentpublishing.application.exception.ContentTagAssignmentRejectedException;
import dev.persefonia.contentpublishing.application.port.AssignableTagOption;
import dev.persefonia.contentpublishing.application.port.ContentTagVocabularyPort;
import dev.persefonia.contentpublishing.application.port.ReferencedTagDetails;
import dev.persefonia.contentpublishing.application.port.TagAssignmentValidation;
import dev.persefonia.contentpublishing.application.service.ContentTagAssignmentService;
import dev.persefonia.contentpublishing.domain.common.AdminIdentityRef;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentStatus;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import dev.persefonia.contentpublishing.domain.content.Slug;
import dev.persefonia.contentpublishing.domain.content.TagId;
import dev.persefonia.contentpublishing.domain.content.port.ContentItemRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContentTagAssignmentServiceTest {
    private static final Instant NOW = Instant.parse("2026-06-15T10:00:00Z");
    private static final ContentCommandActor OWNER =
            new ContentCommandActor(AdminIdentityRef.newId(), true, true);
    private static final ContentCommandActor EDITOR =
            new ContentCommandActor(AdminIdentityRef.newId(), true, false);

    private final Items items = new Items();
    private final Vocabulary vocabulary = new Vocabulary();
    private final ContentTagAssignmentService service = new ContentTagAssignmentService(
            items,
            vocabulary,
            (actor, command) -> {
                if (!actor.active() || !actor.owner()) {
                    throw new SecurityException("OWNER required");
                }
            });
    private final ContentId contentId = items.addDraft();

    @Test
    void ownerReplacesTagsThroughAggregateRepositoryAndDuplicatesCollapse() {
        TagId first = vocabulary.active("First");
        TagId second = vocabulary.active("Second");

        service.assign(command(OWNER, contentId, List.of(first, first, second)));

        assertThat(items.values.get(contentId).tagIds()).containsExactlyInAnyOrder(first, second);
        assertThat(items.saveCount).isEqualTo(1);
        assertThat(items.values.get(contentId).updatedAt()).isEqualTo(NOW);
    }

    @Test
    void nonOwnerIsRejectedBeforeMutationAndMissingContentUsesExistingBehavior() {
        TagId tag = vocabulary.active("Tag");

        assertThatThrownBy(() -> service.assign(command(EDITOR, contentId, List.of(tag))))
                .isInstanceOf(SecurityException.class);
        assertThat(items.values.get(contentId).tagIds()).isEmpty();
        assertThat(items.saveCount).isZero();

        assertThatThrownBy(() -> service.assign(command(OWNER, ContentId.newId(), List.of(tag))))
                .isInstanceOf(ContentNotFoundException.class);
        assertThat(items.saveCount).isZero();
    }

    @Test
    void missingAndNewArchivedTagsAreRejectedWithoutMutation() {
        TagId existing = vocabulary.active("Existing");
        items.values.get(contentId).replaceTags(Set.of(existing), NOW.minusSeconds(1));

        assertThatThrownBy(() -> service.assign(command(OWNER, contentId, List.of(TagId.newId()))))
                .isInstanceOf(ContentTagAssignmentRejectedException.class)
                .extracting(exception -> ((ContentTagAssignmentRejectedException) exception).reason())
                .isEqualTo(ContentTagAssignmentRejectedException.Reason.MISSING_TAG);

        TagId archived = vocabulary.archived("Archived");
        assertThatThrownBy(() -> service.assign(command(OWNER, contentId, List.of(archived))))
                .isInstanceOf(ContentTagAssignmentRejectedException.class)
                .extracting(exception -> ((ContentTagAssignmentRejectedException) exception).reason())
                .isEqualTo(ContentTagAssignmentRejectedException.Reason.ARCHIVED_TAG);

        assertThat(items.values.get(contentId).tagIds()).containsExactly(existing);
        assertThat(items.saveCount).isZero();
    }

    @Test
    void existingArchivedTagMayRemainOrBeRemovedButCannotLaterBeReadded() {
        TagId archived = vocabulary.archived("Archived");
        items.values.get(contentId).replaceTags(Set.of(archived), NOW.minusSeconds(2));

        service.assign(command(OWNER, contentId, List.of(archived)));
        assertThat(items.values.get(contentId).tagIds()).containsExactly(archived);

        service.assign(command(OWNER, contentId, List.of()));
        assertThat(items.values.get(contentId).tagIds()).isEmpty();

        assertThatThrownBy(() -> service.assign(command(OWNER, contentId, List.of(archived))))
                .isInstanceOf(ContentTagAssignmentRejectedException.class)
                .extracting(exception -> ((ContentTagAssignmentRejectedException) exception).reason())
                .isEqualTo(ContentTagAssignmentRejectedException.Reason.ARCHIVED_TAG);
    }

    @Test
    void archivedContentIsRejectedAsControlledApplicationFailureBeforeVocabularyValidation() {
        ContentId archivedId = items.addArchived();
        TagId requested = vocabulary.active("Requested");

        assertThatThrownBy(() -> service.assign(command(OWNER, archivedId, List.of(requested))))
                .isInstanceOf(ContentTagAssignmentRejectedException.class)
                .extracting(exception -> ((ContentTagAssignmentRejectedException) exception).reason())
                .isEqualTo(ContentTagAssignmentRejectedException.Reason.CONTENT_NOT_EDITABLE);
        assertThat(vocabulary.validationCalls).isZero();
        assertThat(items.saveCount).isZero();
    }

    @Test
    void viewUsesTagIdsFromLoadedAggregate() {
        TagId assigned = vocabulary.archived("Assigned");
        items.values.get(contentId).replaceTags(Set.of(assigned), NOW.minusSeconds(1));

        var view = service.view(OWNER, contentId);

        assertThat(view.assignedTags()).extracting(ReferencedTagDetails::id).containsExactly(assigned);
        assertThat(vocabulary.lastDetailsRequest).containsExactly(assigned);
        assertThat(items.saveCount).isZero();
    }

    @Test
    void maximumTagPolicyIsEnforcedAfterDeduplication() {
        TagId duplicate = vocabulary.active("Duplicate");
        service.assign(command(OWNER, contentId, java.util.Collections.nCopies(30, duplicate)));
        assertThat(items.values.get(contentId).tagIds()).containsExactly(duplicate);

        List<TagId> requested = new ArrayList<>();
        for (int index = 0; index <= ContentTagAssignmentService.MAX_TAGS; index++) {
            requested.add(vocabulary.active("Tag " + index));
        }
        assertThatThrownBy(() -> service.assign(command(OWNER, contentId, requested)))
                .isInstanceOf(ContentTagAssignmentRejectedException.class)
                .extracting(exception -> ((ContentTagAssignmentRejectedException) exception).reason())
                .isEqualTo(ContentTagAssignmentRejectedException.Reason.TOO_MANY_TAGS);
    }

    private static AssignContentTagsCommand command(
            ContentCommandActor actor, ContentId contentId, List<TagId> tagIds) {
        return new AssignContentTagsCommand(actor, contentId, tagIds, NOW);
    }

    private static final class Vocabulary implements ContentTagVocabularyPort {
        private final Map<TagId, ReferencedTagDetails> values = new LinkedHashMap<>();
        private int validationCalls;
        private Set<TagId> lastDetailsRequest = Set.of();

        TagId active(String name) { return add(name, false); }
        TagId archived(String name) { return add(name, true); }

        private TagId add(String name, boolean archived) {
            TagId id = TagId.newId();
            values.put(id, new ReferencedTagDetails(id, name, name.toLowerCase().replace(' ', '-'), archived));
            return id;
        }

        @Override
        public List<AssignableTagOption> findAssignableTags() {
            return values.values().stream()
                    .filter(tag -> !tag.archived())
                    .map(tag -> new AssignableTagOption(tag.id(), tag.name(), tag.slug()))
                    .toList();
        }

        @Override
        public List<ReferencedTagDetails> findByIds(Set<TagId> ids) {
            lastDetailsRequest = Set.copyOf(ids);
            return ids.stream().map(values::get).filter(java.util.Objects::nonNull).toList();
        }

        @Override
        public TagAssignmentValidation validateAssignments(Set<TagId> current, Set<TagId> requested) {
            validationCalls++;
            Set<TagId> missing = new LinkedHashSet<>(requested);
            missing.removeAll(values.keySet());
            Set<TagId> archived = new LinkedHashSet<>();
            requested.stream()
                    .filter(id -> values.containsKey(id) && values.get(id).archived() && !current.contains(id))
                    .forEach(archived::add);
            return new TagAssignmentValidation(missing, archived);
        }
    }

    private static final class Items implements ContentItemRepository {
        private final Map<ContentId, ContentItem> values = new LinkedHashMap<>();
        private int saveCount;

        ContentId addDraft() {
            ContentItem item = ContentItem.createDraft(
                    ContentId.newId(), ContentType.ARTICLE, ContentVisibility.PRIVATE, ContentLanguage.EN, NOW.minusSeconds(10));
            values.put(item.id(), item);
            return item.id();
        }

        ContentId addArchived() {
            ContentId id = addDraft();
            values.get(id).archive(NOW.minusSeconds(1));
            return id;
        }

        @Override public ContentItem save(ContentItem item) { saveCount++; values.put(item.id(), item); return item; }
        @Override public Optional<ContentItem> findById(ContentId id) { return Optional.ofNullable(values.get(id)); }
        @Override public Optional<ContentItem> findBySlugAndTypeAndLanguage(Slug slug, ContentType type, ContentLanguage language) { return Optional.empty(); }
        @Override public Optional<ContentItem> findPublishedByRoute(ContentType type, Slug slug, ContentLanguage language) { return Optional.empty(); }
        @Override public List<ContentItem> findDrafts() { return List.of(); }
        @Override public List<ContentItem> findByStatus(ContentStatus status) { return List.of(); }
        @Override public List<ContentItem> findByAssignedTagId(TagId tagId) { return List.of(); }
        @Override public boolean existsSlugInNamespace(ContentType type, ContentLanguage language, Slug slug) { return false; }
    }
}
