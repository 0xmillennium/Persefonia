package dev.persefonia.taxonomy.application.service;

import dev.persefonia.taxonomy.application.query.TagVocabularyItem;
import dev.persefonia.taxonomy.domain.model.Tag;
import dev.persefonia.taxonomy.domain.model.TagId;
import dev.persefonia.taxonomy.domain.model.TagStatus;
import dev.persefonia.taxonomy.domain.port.TagRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class TagVocabularyQueryService {
    private final TagRepository tags;

    public TagVocabularyQueryService(TagRepository tags) {
        this.tags = Objects.requireNonNull(tags, "tags");
    }

    public List<TagVocabularyItem> findAssignableTags() {
        return tags.findAllForAdmin().stream()
                .filter(tag -> tag.status() == TagStatus.ACTIVE)
                .map(TagVocabularyQueryService::item)
                .sorted(Comparator.comparing(TagVocabularyItem::name))
                .toList();
    }

    public List<TagVocabularyItem> findByIds(Set<TagId> ids) {
        Objects.requireNonNull(ids, "ids");
        return ids.stream()
                .map(tags::findById)
                .flatMap(java.util.Optional::stream)
                .map(TagVocabularyQueryService::item)
                .sorted(Comparator.comparing(TagVocabularyItem::name))
                .toList();
    }

    private static TagVocabularyItem item(Tag tag) {
        return new TagVocabularyItem(tag.id(), tag.name().value(), tag.slug().value(), tag.status());
    }
}
