package dev.persefonia.app.webadmin.content;

import dev.persefonia.contentpublishing.application.port.AssignableTagOption;
import dev.persefonia.contentpublishing.application.port.ContentTagAssignmentStore;
import dev.persefonia.contentpublishing.application.port.ContentTagVocabularyPort;
import dev.persefonia.contentpublishing.application.port.ReferencedTagDetails;
import dev.persefonia.contentpublishing.application.port.TagAssignmentValidation;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ReferencedTagId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class AdminContentTagAssignmentTestSupport implements ContentTagAssignmentStore, ContentTagVocabularyPort {
    private final Map<ReferencedTagId, ReferencedTagDetails> tags = new LinkedHashMap<>();
    private final Map<ContentId, Set<ReferencedTagId>> assignments = new LinkedHashMap<>();

    ReferencedTagId active(String name) {
        return add(name, false);
    }

    ReferencedTagId archived(String name) {
        return add(name, true);
    }

    void assign(ContentId contentId, ReferencedTagId tagId) {
        assignments.computeIfAbsent(contentId, ignored -> new LinkedHashSet<>()).add(tagId);
    }

    Set<ReferencedTagId> assigned(ContentId contentId) {
        return findAssignedTagIds(contentId);
    }

    void reset() {
        tags.clear();
        assignments.clear();
    }

    @Override
    public Set<ReferencedTagId> findAssignedTagIds(ContentId contentId) {
        return Set.copyOf(assignments.getOrDefault(contentId, Set.of()));
    }

    @Override
    public void replaceAssignedTagIds(ContentId contentId, Set<ReferencedTagId> tagIds, Instant assignedAt) {
        assignments.put(contentId, new LinkedHashSet<>(tagIds));
    }

    @Override
    public List<AssignableTagOption> findAssignableTags() {
        return tags.values().stream()
                .filter(tag -> !tag.archived())
                .map(tag -> new AssignableTagOption(tag.id(), tag.name(), tag.slug()))
                .toList();
    }

    @Override
    public List<ReferencedTagDetails> findByIds(Set<ReferencedTagId> ids) {
        return ids.stream().map(tags::get).filter(java.util.Objects::nonNull).toList();
    }

    @Override
    public TagAssignmentValidation validateAssignments(Set<ReferencedTagId> current, Set<ReferencedTagId> requested) {
        Set<ReferencedTagId> missing = new LinkedHashSet<>(requested);
        missing.removeAll(tags.keySet());
        Set<ReferencedTagId> newlyArchived = new LinkedHashSet<>();
        requested.stream()
                .filter(id -> tags.containsKey(id) && tags.get(id).archived() && !current.contains(id))
                .forEach(newlyArchived::add);
        return new TagAssignmentValidation(missing, newlyArchived);
    }

    private ReferencedTagId add(String name, boolean archived) {
        ReferencedTagId id = ReferencedTagId.from(UUID.randomUUID());
        tags.put(id, new ReferencedTagDetails(id, name, name.toLowerCase().replace(' ', '-'), archived));
        return id;
    }
}
