package dev.persefonia.app.webadmin.content;

import dev.persefonia.contentpublishing.application.port.AssignableTagOption;
import dev.persefonia.contentpublishing.application.port.ContentTagVocabularyPort;
import dev.persefonia.contentpublishing.application.port.ReferencedTagDetails;
import dev.persefonia.contentpublishing.application.port.TagAssignmentValidation;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.TagId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class AdminContentTagAssignmentTestSupport implements ContentTagVocabularyPort {
    private final AdminContentTestRepository contentItems;
    private final Map<TagId, ReferencedTagDetails> tags = new LinkedHashMap<>();

    AdminContentTagAssignmentTestSupport(AdminContentTestRepository contentItems) {
        this.contentItems = contentItems;
    }

    TagId active(String name) {
        return add(name, false);
    }

    TagId archived(String name) {
        return add(name, true);
    }

    void assign(ContentId contentId, TagId tagId) {
        var content = contentItems.findById(contentId).orElseThrow();
        Set<TagId> assigned = new LinkedHashSet<>(content.tagIds());
        assigned.add(tagId);
        content.replaceTags(assigned, Instant.parse("2026-06-15T09:00:00Z"));
    }

    Set<TagId> assigned(ContentId contentId) {
        return contentItems.findById(contentId).orElseThrow().tagIds();
    }

    void reset() {
        tags.clear();
    }

    @Override
    public List<AssignableTagOption> findAssignableTags() {
        return tags.values().stream()
                .filter(tag -> !tag.archived())
                .map(tag -> new AssignableTagOption(tag.id(), tag.name(), tag.slug()))
                .toList();
    }

    @Override
    public List<ReferencedTagDetails> findByIds(Set<TagId> ids) {
        return ids.stream().map(tags::get).filter(java.util.Objects::nonNull).toList();
    }

    @Override
    public TagAssignmentValidation validateAssignments(Set<TagId> current, Set<TagId> requested) {
        Set<TagId> missing = new LinkedHashSet<>(requested);
        missing.removeAll(tags.keySet());
        Set<TagId> newlyArchived = new LinkedHashSet<>();
        requested.stream()
                .filter(id -> tags.containsKey(id) && tags.get(id).archived() && !current.contains(id))
                .forEach(newlyArchived::add);
        return new TagAssignmentValidation(missing, newlyArchived);
    }

    private TagId add(String name, boolean archived) {
        TagId id = TagId.from(UUID.randomUUID());
        tags.put(id, new ReferencedTagDetails(id, name, name.toLowerCase().replace(' ', '-'), archived));
        return id;
    }
}
