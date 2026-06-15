package dev.persefonia.contentpublishing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.contentpublishing.application.authorization.ContentCommandActor;
import dev.persefonia.contentpublishing.application.command.AssignContentTagsCommand;
import dev.persefonia.contentpublishing.application.exception.ContentNotFoundException;
import dev.persefonia.contentpublishing.application.exception.ContentTagAssignmentRejectedException;
import dev.persefonia.contentpublishing.application.port.AssignableTagOption;
import dev.persefonia.contentpublishing.application.port.ContentTagAssignmentStore;
import dev.persefonia.contentpublishing.application.port.ContentTagVocabularyPort;
import dev.persefonia.contentpublishing.application.port.ReferencedTagDetails;
import dev.persefonia.contentpublishing.application.port.TagAssignmentValidation;
import dev.persefonia.contentpublishing.application.service.ContentTagAssignmentService;
import dev.persefonia.contentpublishing.domain.common.AdminIdentityRef;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import dev.persefonia.contentpublishing.domain.content.ReferencedTagId;
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
            new ContentCommandActor(AdminIdentityRef.from(UUID.randomUUID()), true, true);
    private static final ContentCommandActor EDITOR =
            new ContentCommandActor(AdminIdentityRef.from(UUID.randomUUID()), true, false);

    private final Items items = new Items();
    private final Assignments assignments = new Assignments();
    private final Vocabulary vocabulary = new Vocabulary();
    private final ContentTagAssignmentService service = new ContentTagAssignmentService(
            items, assignments, vocabulary, (actor, command) -> {
                if (!actor.active() || !actor.owner()) throw new SecurityException("OWNER required");
            });
    private final ContentId contentId = items.add();

    @Test
    void ownerCanReplaceActiveTagsAndDuplicatesCollapse() {
        ReferencedTagId first = vocabulary.active("First");
        ReferencedTagId second = vocabulary.active("Second");

        service.assign(command(OWNER, contentId, List.of(first, first, second)));

        assertThat(assignments.values.get(contentId)).containsExactlyInAnyOrder(first, second);
    }

    @Test
    void nonOwnerAndMissingContentAreRejected() {
        ReferencedTagId tag = vocabulary.active("Tag");
        assertThatThrownBy(() -> service.assign(command(EDITOR, contentId, List.of(tag))))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.assign(command(OWNER, ContentId.newId(), List.of(tag))))
                .isInstanceOf(ContentNotFoundException.class);
    }

    @Test
    void missingAndNewArchivedTagsAreRejectedWithoutChangingAssignments() {
        ReferencedTagId existing = vocabulary.active("Existing");
        assignments.values.put(contentId, new LinkedHashSet<>(Set.of(existing)));

        assertThatThrownBy(() -> service.assign(command(OWNER, contentId, List.of(ReferencedTagId.from(UUID.randomUUID())))))
                .isInstanceOf(ContentTagAssignmentRejectedException.class);
        assertThat(assignments.values.get(contentId)).containsExactly(existing);

        ReferencedTagId archived = vocabulary.archived("Archived");
        assertThatThrownBy(() -> service.assign(command(OWNER, contentId, List.of(archived))))
                .isInstanceOf(ContentTagAssignmentRejectedException.class);
        assertThat(assignments.values.get(contentId)).containsExactly(existing);
    }

    @Test
    void currentlyAssignedArchivedTagMayRemainOrBeRemoved() {
        ReferencedTagId archived = vocabulary.archived("Archived");
        assignments.values.put(contentId, new LinkedHashSet<>(Set.of(archived)));

        service.assign(command(OWNER, contentId, List.of(archived)));
        assertThat(assignments.values.get(contentId)).containsExactly(archived);

        service.assign(command(OWNER, contentId, List.of()));
        assertThat(assignments.values.get(contentId)).isEmpty();
    }

    @Test
    void tooManyTagsAreRejected() {
        List<ReferencedTagId> requested = new ArrayList<>();
        for (int index = 0; index <= ContentTagAssignmentService.MAX_TAGS; index++) {
            requested.add(vocabulary.active("Tag " + index));
        }
        assertThatThrownBy(() -> service.assign(command(OWNER, contentId, requested)))
                .isInstanceOf(ContentTagAssignmentRejectedException.class)
                .extracting(exception -> ((ContentTagAssignmentRejectedException) exception).reason())
                .isEqualTo(ContentTagAssignmentRejectedException.Reason.TOO_MANY_TAGS);
    }

    private static AssignContentTagsCommand command(
            ContentCommandActor actor, ContentId contentId, List<ReferencedTagId> tagIds) {
        return new AssignContentTagsCommand(actor, contentId, tagIds, NOW);
    }

    private static final class Assignments implements ContentTagAssignmentStore {
        private final Map<ContentId, Set<ReferencedTagId>> values = new LinkedHashMap<>();
        @Override public Set<ReferencedTagId> findAssignedTagIds(ContentId id) {
            return Set.copyOf(values.getOrDefault(id, Set.of()));
        }
        @Override public void replaceAssignedTagIds(ContentId id, Set<ReferencedTagId> ids, Instant assignedAt) {
            values.put(id, new LinkedHashSet<>(ids));
        }
    }

    private static final class Vocabulary implements ContentTagVocabularyPort {
        private final Map<ReferencedTagId, ReferencedTagDetails> values = new LinkedHashMap<>();
        ReferencedTagId active(String name) { return add(name, false); }
        ReferencedTagId archived(String name) { return add(name, true); }
        private ReferencedTagId add(String name, boolean archived) {
            ReferencedTagId id = ReferencedTagId.from(UUID.randomUUID());
            values.put(id, new ReferencedTagDetails(id, name, name.toLowerCase().replace(' ', '-'), archived));
            return id;
        }
        @Override public List<AssignableTagOption> findAssignableTags() { return List.of(); }
        @Override public List<ReferencedTagDetails> findByIds(Set<ReferencedTagId> ids) {
            return ids.stream().map(values::get).filter(java.util.Objects::nonNull).toList();
        }
        @Override public TagAssignmentValidation validateAssignments(Set<ReferencedTagId> current, Set<ReferencedTagId> requested) {
            Set<ReferencedTagId> missing = new LinkedHashSet<>(requested);
            missing.removeAll(values.keySet());
            Set<ReferencedTagId> archived = new LinkedHashSet<>();
            requested.stream()
                    .filter(id -> values.containsKey(id) && values.get(id).archived() && !current.contains(id))
                    .forEach(archived::add);
            return new TagAssignmentValidation(missing, archived);
        }
    }

    private static final class Items implements ContentItemRepository {
        private final Map<ContentId, ContentItem> values = new LinkedHashMap<>();
        ContentId add() {
            ContentId id = ContentId.newId();
            values.put(id, ContentItem.createDraft(id, ContentType.ARTICLE, ContentVisibility.PRIVATE, ContentLanguage.EN, NOW));
            return id;
        }
        @Override public ContentItem save(ContentItem item) { values.put(item.id(), item); return item; }
        @Override public Optional<ContentItem> findById(ContentId id) { return Optional.ofNullable(values.get(id)); }
        @Override public Optional<ContentItem> findBySlugAndTypeAndLanguage(
                dev.persefonia.contentpublishing.domain.content.Slug slug, ContentType type, ContentLanguage language) {
            return Optional.empty();
        }
        @Override public Optional<ContentItem> findPublishedByRoute(
                ContentType type, dev.persefonia.contentpublishing.domain.content.Slug slug, ContentLanguage language) {
            return Optional.empty();
        }
        @Override public List<ContentItem> findDrafts() { return List.of(); }
        @Override public List<ContentItem> findByStatus(dev.persefonia.contentpublishing.domain.content.ContentStatus status) {
            return List.of();
        }
        @Override public List<ContentItem> findByAssignedTagId(
                dev.persefonia.contentpublishing.domain.content.TagId tagId) {
            return List.of();
        }
        @Override public boolean existsSlugInNamespace(
                ContentType type, ContentLanguage language, dev.persefonia.contentpublishing.domain.content.Slug slug) {
            return false;
        }
    }
}
